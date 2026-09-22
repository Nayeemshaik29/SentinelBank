package com.sentinelbank.fraud;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.fraud.domain.FraudCase;
import com.sentinelbank.fraud.domain.FraudCaseRepository;
import com.sentinelbank.fraud.domain.ProcessedTransferRepository;
import com.sentinelbank.fraud.kafka.TransferInitiatedPayload;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the rule engine and the idempotent consumer against a real MongoDB and a real Testcontainers
 * Kafka: each rule fires (and only that rule, where a test is designed to isolate one), redelivery never
 * creates a duplicate case, and a message that can never be processed still reaches the DLT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferInitiatedListenerTests {

	private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		KAFKA.start();
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		registry.add("sentinelbank.kafka.retry.backoff-ms", () -> "100");
		registry.add("sentinelbank.kafka.retry-topic.backoff-ms", () -> "200");
	}

	@AfterAll
	static void stopKafka() {
		KAFKA.stop();
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private FraudCaseRepository fraudCases;

	@Autowired
	private ProcessedTransferRepository processedTransfers;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	void ensureTopicsExist() throws Exception {
		try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
			List<NewTopic> topics = Stream.of(Topics.TRANSFER_INITIATED)
					.flatMap(topic -> Stream.of(topic, Topics.retry(topic), Topics.dlt(topic)))
					.map(topic -> new NewTopic(topic, 3, (short) 1))
					.toList();
			admin.createTopics(topics).all().get();
		}
		catch (ExecutionException ex) {
			if (!(ex.getCause() instanceof TopicExistsException)) {
				throw ex;
			}
		}
	}

	@Test
	void aLargeAmountOpensACaseWithReasonLargeAmount() throws Exception {
		UUID transferId = UUID.randomUUID();
		produce(transferId, UUID.randomUUID(), "PARTNER-" + UUID.randomUUID(), 543_210L, Instant.now());

		FraudCase fraudCase = awaitCase(transferId);
		assertThat(fraudCase.getReasons()).contains("LARGE_AMOUNT").doesNotContain("ROUND_AMOUNT");
	}

	@Test
	void aRoundAmountBelowTheLargeThresholdOpensACaseWithReasonRoundAmountOnly() throws Exception {
		UUID transferId = UUID.randomUUID();
		produce(transferId, UUID.randomUUID(), "PARTNER-" + UUID.randomUUID(), 300_000L, Instant.now());

		FraudCase fraudCase = awaitCase(transferId);
		assertThat(fraudCase.getReasons()).contains("ROUND_AMOUNT").doesNotContain("LARGE_AMOUNT");
	}

	@Test
	void anOrdinaryAmountToAnAlreadyKnownBeneficiaryOpensNoCase() throws Exception {
		UUID fromAccountId = UUID.randomUUID();
		String toAccountId = "PARTNER-" + UUID.randomUUID();
		Instant first = Instant.now().minus(Duration.ofMinutes(30));
		UUID firstTransferId = UUID.randomUUID();
		produce(firstTransferId, fromAccountId, toAccountId, 12_345L, first);
		awaitCase(firstTransferId); // the very first transfer to anyone is always a new beneficiary

		UUID secondTransferId = UUID.randomUUID();
		// well outside the velocity window, ordinary amount, same (now-known) beneficiary
		produce(secondTransferId, fromAccountId, toAccountId, 12_345L, first.plus(Duration.ofMinutes(20)));
		awaitProcessed(secondTransferId);

		assertThat(fraudCases.findById(secondTransferId.toString())).as("nothing about this transfer is unusual")
				.isEmpty();
	}

	@Test
	void threeRapidTransfersFromTheSameAccountTriggerVelocityOnTheThird() throws Exception {
		UUID fromAccountId = UUID.randomUUID();
		String toAccountId = "PARTNER-" + UUID.randomUUID();
		Instant t0 = Instant.now();

		UUID first = UUID.randomUUID();
		produce(first, fromAccountId, toAccountId, 5_000L, t0);
		awaitProcessed(first);

		UUID second = UUID.randomUUID();
		produce(second, fromAccountId, toAccountId, 5_000L, t0.plusSeconds(10));
		awaitProcessed(second);
		assertThat(fraudCases.findById(second.toString()).map(FraudCase::getReasons).orElse(List.of()))
				.as("only 2 transfers so far: velocity has not tripped yet").doesNotContain("VELOCITY");

		UUID third = UUID.randomUUID();
		produce(third, fromAccountId, toAccountId, 5_000L, t0.plusSeconds(20));
		FraudCase thirdCase = awaitCase(third);
		assertThat(thirdCase.getReasons()).as("this transfer plus the 2 before it, all inside the window")
				.contains("VELOCITY");
	}

	@Test
	void redeliveringTheSameTransferNeverOpensASecondCase() throws Exception {
		UUID transferId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		produce(transferId, fromAccountId, "PARTNER-" + UUID.randomUUID(), 999_999L, Instant.now());
		awaitCase(transferId);

		produce(transferId, fromAccountId, "PARTNER-" + UUID.randomUUID(), 999_999L, Instant.now());
		Thread.sleep(1000);

		assertThat(fraudCases.findAll().stream().filter(c -> c.getTransferId().equals(transferId))).hasSize(1);
	}

	@Test
	void openCasesAreListedThroughTheApi() throws Exception {
		UUID transferId = UUID.randomUUID();
		produce(transferId, UUID.randomUUID(), "PARTNER-" + UUID.randomUUID(), 700_000L, Instant.now());
		awaitCase(transferId);

		String body = mvc.perform(get("/fraud/cases")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> transferIds = JsonPath.read(body, "$[*].transferId");
		assertThat(transferIds).contains(transferId.toString());
		// A filter predicate switches JsonPath into "indefinite" (many-results) mode for the rest of the
		// path, so a trailing [0] on the same expression does not collapse it back to a single value —
		// read the filtered list and take its first element instead.
		List<String> statuses = JsonPath.read(body, "$[?(@.transferId=='" + transferId + "')].status");
		assertThat(statuses).first().isEqualTo("OPEN");
	}

	@Test
	void aMessageThatCanNeverBeProcessedEndsUpOnTheDeadLetterTopic() throws Exception {
		String marker = "poison-" + UUID.randomUUID();
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
			producer.send(new ProducerRecord<>(Topics.TRANSFER_INITIATED, "not valid json: " + marker))
					.get(5, TimeUnit.SECONDS);
		}

		List<ConsumerRecord<String, String>> onDlt = consumeMatching(Topics.dlt(Topics.TRANSFER_INITIATED), marker);
		assertThat(onDlt).hasSize(1);
	}

	private void produce(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
			Instant occurredAt) throws Exception {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
			TransferInitiatedPayload payload = new TransferInitiatedPayload(transferId, fromAccountId, toAccountId,
					amountMinor, "USD");
			EventEnvelope<TransferInitiatedPayload> envelope = new EventEnvelope<>(UUID.randomUUID(),
					Topics.TRANSFER_INITIATED, transferId.toString(), occurredAt, UUID.randomUUID().toString(),
					payload);
			String json = objectMapper.writeValueAsString(envelope);
			producer.send(new ProducerRecord<>(Topics.TRANSFER_INITIATED, fromAccountId.toString(), json))
					.get(5, TimeUnit.SECONDS);
		}
	}

	private FraudCase awaitCase(UUID transferId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			var found = fraudCases.findById(transferId.toString());
			if (found.isPresent()) {
				return found.get();
			}
			Thread.sleep(200);
		}
		throw new AssertionError("No fraud case appeared for transfer " + transferId);
	}

	private void awaitProcessed(UUID transferId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			if (processedTransfers.existsById(transferId.toString())) {
				return;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Transfer " + transferId + " was never recorded as processed");
	}

	private List<ConsumerRecord<String, String>> consumeMatching(String topic, String marker) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(topic));
			List<ConsumerRecord<String, String>> collected = new ArrayList<>();
			long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
			int consecutiveEmptyPolls = 0;
			while (System.currentTimeMillis() < deadline) {
				ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(300));
				batch.forEach(collected::add);
				consecutiveEmptyPolls = batch.isEmpty() ? consecutiveEmptyPolls + 1 : 0;
				boolean foundMarker = collected.stream().anyMatch(record -> record.value().contains(marker));
				if (foundMarker && consecutiveEmptyPolls >= 2) {
					break;
				}
			}
			return collected.stream().filter(record -> record.value().contains(marker)).toList();
		}
	}
}
