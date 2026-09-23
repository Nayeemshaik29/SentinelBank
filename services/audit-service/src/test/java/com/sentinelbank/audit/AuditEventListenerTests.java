package com.sentinelbank.audit;

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
import com.sentinelbank.audit.domain.AuditEvent;
import com.sentinelbank.audit.domain.AuditEventRepository;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;
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
 * Proves the idempotent, append-only consumer against a real MongoDB and a real Testcontainers Kafka: all
 * three topics land in the same trail, a redelivery never creates a duplicate, one transfer's full story
 * comes back in order, and a message that can never be processed still reaches the DLT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditEventListenerTests {

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
	private AuditEventRepository auditEvents;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	void ensureTopicsExist() throws Exception {
		try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
			List<NewTopic> topics = Stream.of(Topics.TRANSFER_INITIATED, Topics.TRANSFER_COMPLETED,
					Topics.TRANSFER_FAILED)
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
	void aTransferInitiatedEventIsRecorded() throws Exception {
		String transferId = UUID.randomUUID().toString();
		UUID eventId = produce(Topics.TRANSFER_INITIATED, transferId,
				Map.of("transferId", transferId, "fromAccountId", UUID.randomUUID().toString(), "amountMinor",
						12_345));

		var recorded = awaitRecorded(eventId);
		assertThat(recorded.getEventType()).isEqualTo(Topics.TRANSFER_INITIATED);
		assertThat(recorded.getAggregateId()).isEqualTo(transferId);
		assertThat(recorded.getPayload()).containsEntry("transferId", transferId);
	}

	@Test
	void aTransferCompletedEventIsRecorded() throws Exception {
		String transferId = UUID.randomUUID().toString();
		UUID eventId = produce(Topics.TRANSFER_COMPLETED, transferId, Map.of("transferId", transferId));

		var recorded = awaitRecorded(eventId);
		assertThat(recorded.getEventType()).isEqualTo(Topics.TRANSFER_COMPLETED);
	}

	@Test
	void aTransferFailedEventIsRecorded() throws Exception {
		String transferId = UUID.randomUUID().toString();
		UUID eventId = produce(Topics.TRANSFER_FAILED, transferId,
				Map.of("transferId", transferId, "reason", "PARTNER_REJECTED"));

		var recorded = awaitRecorded(eventId);
		assertThat(recorded.getEventType()).isEqualTo(Topics.TRANSFER_FAILED);
		assertThat(recorded.getPayload()).containsEntry("reason", "PARTNER_REJECTED");
	}

	@Test
	void redeliveringTheSameEventNeverCreatesADuplicate() throws Exception {
		String transferId = UUID.randomUUID().toString();
		Map<String, Object> payload = Map.of("transferId", transferId);
		UUID eventId = UUID.randomUUID();

		produceWithEventId(eventId, Topics.TRANSFER_INITIATED, transferId, payload);
		awaitRecorded(eventId);

		produceWithEventId(eventId, Topics.TRANSFER_INITIATED, transferId, payload);
		Thread.sleep(1000);

		assertThat(auditEvents.findAllByAggregateIdOrderByOccurredAtAsc(transferId)).hasSize(1);
	}

	@Test
	void oneTransfersFullStoryComesBackInOrder() throws Exception {
		String transferId = UUID.randomUUID().toString();
		produce(Topics.TRANSFER_INITIATED, transferId, Map.of("transferId", transferId));
		Thread.sleep(50); // occurredAt must differ so ordering is unambiguous, not a race we depend on
		UUID completedEventId = produce(Topics.TRANSFER_COMPLETED, transferId, Map.of("transferId", transferId));
		awaitRecorded(completedEventId);

		String body = mvc.perform(get("/audit/events/{aggregateId}", transferId)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> eventTypes = JsonPath.read(body, "$[*].eventType");
		assertThat(eventTypes).containsExactly(Topics.TRANSFER_INITIATED, Topics.TRANSFER_COMPLETED);
	}

	@Test
	void recentEventsAreListedThroughTheApi() throws Exception {
		String transferId = UUID.randomUUID().toString();
		UUID eventId = produce(Topics.TRANSFER_INITIATED, transferId, Map.of("transferId", transferId));
		awaitRecorded(eventId);

		String body = mvc.perform(get("/audit/events").param("limit", "500")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> aggregateIds = JsonPath.read(body, "$[*].aggregateId");
		assertThat(aggregateIds).contains(transferId);
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

	private UUID produce(String topic, String aggregateId, Map<String, Object> payload) throws Exception {
		UUID eventId = UUID.randomUUID();
		produceWithEventId(eventId, topic, aggregateId, payload);
		return eventId;
	}

	private void produceWithEventId(UUID eventId, String topic, String aggregateId, Map<String, Object> payload)
			throws Exception {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
			EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(eventId, topic, aggregateId,
					Instant.now(), UUID.randomUUID().toString(), payload);
			String json = objectMapper.writeValueAsString(envelope);
			producer.send(new ProducerRecord<>(topic, aggregateId, json)).get(5, TimeUnit.SECONDS);
		}
	}

	private AuditEvent awaitRecorded(UUID eventId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			var found = auditEvents.findById(eventId.toString());
			if (found.isPresent()) {
				return found.get();
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Event " + eventId + " was never recorded");
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
