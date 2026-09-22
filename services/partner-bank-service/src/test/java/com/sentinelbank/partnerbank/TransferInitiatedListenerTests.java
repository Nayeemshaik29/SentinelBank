package com.sentinelbank.partnerbank;

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
import com.sentinelbank.partnerbank.domain.CreditOutcome;
import com.sentinelbank.partnerbank.domain.PartnerCreditRepository;
import com.sentinelbank.partnerbank.kafka.TransferInitiatedPayload;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the first real Kafka consumer in this project against a real Postgres and a real Testcontainers
 * Kafka: the happy path (credit and publish transfer.completed), the demo failure trigger (reject and
 * publish transfer.failed), idempotency on redelivery, and the two-tier retry-then-DLT path for a message
 * that can never be processed.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferInitiatedListenerTests {

	private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		KAFKA.start();
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		// Much shorter than the production defaults, so the retry-then-DLT test does not take ~10s.
		registry.add("sentinelbank.kafka.retry.backoff-ms", () -> "100");
		registry.add("sentinelbank.kafka.retry-topic.backoff-ms", () -> "200");
	}

	@AfterAll
	static void stopKafka() {
		KAFKA.stop();
	}

	@Autowired
	private PartnerCreditRepository partnerCredits;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	void ensureTopicsExist() throws Exception {
		try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
			List<NewTopic> topics = Stream
					.of(Topics.TRANSFER_INITIATED, Topics.TRANSFER_COMPLETED, Topics.TRANSFER_FAILED)
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
	void happyPathCreditsAndPublishesTransferCompleted() throws Exception {
		UUID transferId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		produceTransferInitiated(transferId, fromAccountId, "PARTNER-OK", 2500L, "USD");

		List<ConsumerRecord<String, String>> completed = consumeMatching(Topics.TRANSFER_COMPLETED,
				transferId.toString());
		assertThat(completed).hasSize(1);
		ConsumerRecord<String, String> record = completed.get(0);
		assertThat(record.key()).isEqualTo(fromAccountId.toString());
		assertThat((String) JsonPath.read(record.value(), "$.payload.toAccountId")).isEqualTo("PARTNER-OK");
		assertThat(((Number) JsonPath.read(record.value(), "$.payload.amountMinor")).longValue()).isEqualTo(2500L);

		var credit = partnerCredits.findByTransferId(transferId).orElseThrow();
		assertThat(credit.getOutcome()).isEqualTo(CreditOutcome.CREDITED);
	}

	@Test
	void toAccountIdWithTheFailureTriggerPrefixIsRejectedAndPublishesTransferFailed() throws Exception {
		UUID transferId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		produceTransferInitiated(transferId, fromAccountId, "FAIL-ME", 1000L, "USD");

		List<ConsumerRecord<String, String>> failed = consumeMatching(Topics.TRANSFER_FAILED,
				transferId.toString());
		assertThat(failed).hasSize(1);
		assertThat((String) JsonPath.read(failed.get(0).value(), "$.payload.reason")).contains("FAIL-ME");

		var credit = partnerCredits.findByTransferId(transferId).orElseThrow();
		assertThat(credit.getOutcome()).isEqualTo(CreditOutcome.REJECTED);
	}

	@Test
	void redeliveringTheSameTransferIsIdempotent() throws Exception {
		UUID transferId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		produceTransferInitiated(transferId, fromAccountId, "PARTNER-DUP", 750L, "USD");
		produceTransferInitiated(transferId, fromAccountId, "PARTNER-DUP", 750L, "USD");

		List<ConsumerRecord<String, String>> completed = consumeMatching(Topics.TRANSFER_COMPLETED,
				transferId.toString());
		assertThat(completed).as("redelivery never produces a second result event").hasSize(1);
		assertThat(partnerCredits.findAll().stream().filter(c -> c.getTransferId().equals(transferId)).count())
				.as("redelivery never credits twice").isEqualTo(1);
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
		assertThat(onDlt).as("a message that never parses ends up on the DLT after both retry tiers")
				.hasSize(1);

		List<ConsumerRecord<String, String>> onCompleted = consumeMatching(Topics.TRANSFER_COMPLETED, marker);
		assertThat(onCompleted).as("a poison message never produces a spurious result event").isEmpty();
	}

	private void produceTransferInitiated(UUID transferId, UUID fromAccountId, String toAccountId,
			long amountMinor, String currency) throws Exception {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
			TransferInitiatedPayload payload = new TransferInitiatedPayload(transferId, fromAccountId, toAccountId,
					amountMinor, currency);
			EventEnvelope<TransferInitiatedPayload> envelope = new EventEnvelope<>(UUID.randomUUID(),
					Topics.TRANSFER_INITIATED, transferId.toString(), Instant.now(), UUID.randomUUID().toString(),
					payload);
			String json = objectMapper.writeValueAsString(envelope);
			producer.send(new ProducerRecord<>(Topics.TRANSFER_INITIATED, fromAccountId.toString(), json))
					.get(5, TimeUnit.SECONDS);
		}
	}

	/** Reads the whole topic from the beginning and filters by a marker string, so assertions are correct
	 * regardless of what other tests in this class have already published to the same shared topics. */
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
				boolean foundMarker = collected.stream().anyMatch(record -> record.value().contains(marker));
				consecutiveEmptyPolls = batch.isEmpty() ? consecutiveEmptyPolls + 1 : 0;
				if (foundMarker && consecutiveEmptyPolls >= 2) {
					break;
				}
			}
			return collected.stream().filter(record -> record.value().contains(marker)).toList();
		}
	}
}
