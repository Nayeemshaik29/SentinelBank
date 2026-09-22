package com.sentinelbank.transaction;

import java.time.Duration;
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
 * message, correctly keyed, and republishing an already-published row sends nothing further.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OutboxPublisherTests {

	private static StubAccountService stubAccountService;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		stubAccountService = new StubAccountService();
		registry.add("sentinelbank.services.account-url", stubAccountService::url);
	}

	@AfterAll
	static void closeStub() {
		stubAccountService.close();
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

		assertThat(outboxEventRepository.findById(UUID.fromString(transferId)).isEmpty())
				.as("the outbox row's id is its own, not the transfer's").isTrue();
		assertThat(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc())
				.as("exactly one unpublished event was written for this transfer")
				.filteredOn(event -> event.getAggregateId().toString().equals(transferId))
				.hasSize(1);

		outboxPublisher.publishPending();

		List<ConsumerRecord<String, String>> records = consumeFrom(Topics.TRANSFER_INITIATED, 1);
		assertThat(records).hasSize(1);
		ConsumerRecord<String, String> record = records.get(0);
		assertThat(record.key()).as("the message is keyed by accountId").isEqualTo(fromAccountId.toString());
		assertThat((String) JsonPath.read(record.value(), "$.type")).isEqualTo("transfer.initiated");
		assertThat((String) JsonPath.read(record.value(), "$.aggregateId")).isEqualTo(transferId);
		assertThat((String) JsonPath.read(record.value(), "$.payload.transferId")).isEqualTo(transferId);
		assertThat((String) JsonPath.read(record.value(), "$.payload.fromAccountId"))
				.isEqualTo(fromAccountId.toString());
		assertThat((Long) JsonPath.read(record.value(), "$.payload.amountMinor")).isEqualTo(2500L);

		boolean published = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc().stream()
				.noneMatch(event -> event.getAggregateId().toString().equals(transferId));
		assertThat(published).as("the row is marked published so it is not picked up again").isTrue();
	}

	@Test
	void republishingAnAlreadyPublishedEventSendsNothingFurther() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		stubAccountService.addAccount(fromAccountId, ownerId, "USD");

		mvc.perform(post("/transfers").header("X-User-Id", ownerId.toString())
				.header("Idempotency-Key", "outbox-key-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromAccountId\":\"" + fromAccountId + "\",\"toAccountId\":\"PARTNER-1\","
						+ "\"amountMinor\":500}"))
				.andExpect(status().isCreated());

		outboxPublisher.publishPending();
		consumeFrom(Topics.TRANSFER_INITIATED, 1);

		// nothing left unpublished, so a second poll must produce no further Kafka messages
		outboxPublisher.publishPending();
		outboxPublisher.publishPending();

		List<ConsumerRecord<String, String>> extra = consumeFrom(Topics.TRANSFER_INITIATED, 0);
		assertThat(extra).as("the same transfer never produces a second event").isEmpty();
	}

	private List<ConsumerRecord<String, String>> consumeFrom(String topic, int expectedAtLeast) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(topic));
			List<ConsumerRecord<String, String>> collected = new java.util.ArrayList<>();
			long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
			while (System.currentTimeMillis() < deadline) {
				ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(500));
				batch.forEach(collected::add);
				if (collected.size() >= expectedAtLeast && batch.isEmpty()) {
					break;
				}
			}
			return collected;
		}
	}
}
