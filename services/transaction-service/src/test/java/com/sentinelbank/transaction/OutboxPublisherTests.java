package com.sentinelbank.transaction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.jayway.jsonpath.JsonPath;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.transaction.domain.OutboxEventRepository;
import com.sentinelbank.transaction.outbox.OutboxPublisher;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the outbox is a real, working pipeline against a real PostgreSQL and a real Kafka
 * (Testcontainers), not just a row sitting unread in a table: a transfer produces exactly one Kafka
 * message, correctly keyed, and further poll cycles never produce a second one for the same transfer.
 *
 * <p>Each test reads the whole topic from the beginning (a fresh consumer group, {@code earliest}) and then
 * filters by its own transfer id, rather than trying to detect "nothing new happened" against a topic that
 * other tests in this class also publish to — that keeps every assertion correct regardless of test order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OutboxPublisherTests {

	private static StubAccountService stubAccountService;

	private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		stubAccountService = new StubAccountService();
		registry.add("sentinelbank.services.account-url", stubAccountService::url);

		// Registered explicitly here, deterministically, rather than via @ServiceConnection on this
		// container: that annotation did not reliably win over the spring.kafka.bootstrap-servers key
		// already present (with a default) in application.yaml, so the app's own KafkaTemplate ended up
		// pointed at the real docker-compose broker instead of this throwaway one in local testing.
		KAFKA.start();
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
	}

	@AfterAll
	static void closeStub() {
		stubAccountService.close();
		KAFKA.stop();
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private OutboxPublisher outboxPublisher;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@BeforeEach
	void ensureTopicExists() throws Exception {
		try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", bootstrapServers))) {
			admin.createTopics(List.of(new NewTopic(Topics.TRANSFER_INITIATED, 3, (short) 1))).all().get();
		}
		catch (ExecutionException ex) {
			if (!(ex.getCause() instanceof TopicExistsException)) {
				throw ex;
			}
		}
	}

	@Test
	void aDebitedTransferProducesExactlyOneCorrectlyKeyedKafkaMessage() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		stubAccountService.addAccount(fromAccountId, ownerId, "USD");

		MvcResult result = mvc.perform(post("/transfers").header("X-User-Id", ownerId.toString())
				.header("Idempotency-Key", "outbox-key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromAccountId\":\"" + fromAccountId + "\",\"toAccountId\":\"PARTNER-1\","
						+ "\"amountMinor\":2500}"))
				.andExpect(status().isCreated())
				.andReturn();
		String transferId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

		assertThat(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc())
				.as("exactly one unpublished event was written for this transfer, before any publish")
				.filteredOn(event -> event.getAggregateId().toString().equals(transferId))
				.hasSize(1);

		outboxPublisher.publishPending();

		List<ConsumerRecord<String, String>> matching = consumeMatching(Topics.TRANSFER_INITIATED, transferId);
		assertThat(matching).as("exactly one Kafka message for this transfer").hasSize(1);
		ConsumerRecord<String, String> record = matching.get(0);
		assertThat(record.key()).as("the message is keyed by accountId").isEqualTo(fromAccountId.toString());
		assertThat((String) JsonPath.read(record.value(), "$.type")).isEqualTo("transfer.initiated");
		assertThat((String) JsonPath.read(record.value(), "$.aggregateId")).isEqualTo(transferId);
		assertThat((String) JsonPath.read(record.value(), "$.payload.fromAccountId"))
				.isEqualTo(fromAccountId.toString());
		assertThat(((Number) JsonPath.read(record.value(), "$.payload.amountMinor")).longValue()).isEqualTo(2500L);

		boolean stillUnpublished = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc().stream()
				.anyMatch(event -> event.getAggregateId().toString().equals(transferId));
		assertThat(stillUnpublished).as("the row is marked published so it is not picked up again").isFalse();
	}

	@Test
	void repeatedPollCyclesNeverProduceASecondEventForTheSameTransfer() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		stubAccountService.addAccount(fromAccountId, ownerId, "USD");

		MvcResult result = mvc.perform(post("/transfers").header("X-User-Id", ownerId.toString())
				.header("Idempotency-Key", "outbox-key-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromAccountId\":\"" + fromAccountId + "\",\"toAccountId\":\"PARTNER-1\","
						+ "\"amountMinor\":500}"))
				.andExpect(status().isCreated())
				.andReturn();
		String transferId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

		outboxPublisher.publishPending();
		outboxPublisher.publishPending();
		outboxPublisher.publishPending();

		List<ConsumerRecord<String, String>> matching = consumeMatching(Topics.TRANSFER_INITIATED, transferId);
		assertThat(matching).as("three poll cycles, still exactly one event for this transfer").hasSize(1);
	}

	private List<ConsumerRecord<String, String>> consumeMatching(String topic, String transferId) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(topic));
			List<ConsumerRecord<String, String>> collected = new ArrayList<>();
			long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
			int consecutiveEmptyPolls = 0;
			while (System.currentTimeMillis() < deadline) {
				ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(300));
				batch.forEach(collected::add);
				consecutiveEmptyPolls = batch.isEmpty() ? consecutiveEmptyPolls + 1 : 0;
				// Only exit early once something has actually been seen: the first poll(s) right after
				// subscribe() are often empty just completing the consumer-group rebalance, not because
				// there is truly nothing on the topic yet. Before that, always wait out the full deadline.
				if (!collected.isEmpty() && consecutiveEmptyPolls >= 2) {
					break;
				}
			}
			return collected.stream()
					.filter(record -> transferId.equals(JsonPath.read(record.value(), "$.payload.transferId")))
					.toList();
		}
	}
}
