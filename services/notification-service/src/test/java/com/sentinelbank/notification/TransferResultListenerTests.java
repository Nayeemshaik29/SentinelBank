package com.sentinelbank.notification;

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
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.notification.domain.NotificationLog;
import com.sentinelbank.notification.domain.NotificationLogRepository;
import com.sentinelbank.notification.domain.NotificationType;
import com.sentinelbank.notification.kafka.TransferResultPayload;
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
import org.mockito.Mockito;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Proves the idempotent notification consumer against a real PostgreSQL and a real Testcontainers Kafka,
 * plus a tiny fake account-service/auth-service: a completed or failed transfer resolves its recipient,
 * sends an email, and records it exactly once even under redelivery; a message that can never be processed
 * still reaches the DLT.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TransferResultListenerTests.MailTestConfig.class })
class TransferResultListenerTests {

	private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

	private static final StubUpstreamServices UPSTREAM = new StubUpstreamServices();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		KAFKA.start();
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		registry.add("sentinelbank.kafka.retry.backoff-ms", () -> "100");
		registry.add("sentinelbank.kafka.retry-topic.backoff-ms", () -> "200");
		registry.add("sentinelbank.services.account", UPSTREAM::url);
		registry.add("sentinelbank.services.auth", UPSTREAM::url);
	}

	@AfterAll
	static void stopInfra() {
		KAFKA.stop();
		UPSTREAM.close();
	}

	@TestConfiguration
	static class MailTestConfig {

		@Bean
		JavaMailSender mailSender() {
			return Mockito.mock(JavaMailSender.class);
		}
	}

	@Autowired
	private NotificationLogRepository notificationLogs;

	@Autowired
	private JavaMailSender mailSender;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	void ensureTopicsExist() throws Exception {
		try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
			List<NewTopic> topics = Stream.of(Topics.TRANSFER_COMPLETED, Topics.TRANSFER_FAILED)
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
	void aCompletedTransferEmailsTheOwnerAndRecordsIt() throws Exception {
		UUID transferId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		UPSTREAM.addAccount(fromAccountId, ownerId);
		UPSTREAM.addUser(ownerId, "completed-" + ownerId + "@example.com", "Ada Customer");

		produce(Topics.TRANSFER_COMPLETED, transferId, fromAccountId, "PARTNER-1", 543_210L, null);

		verify(mailSender, timeout(15_000)).send(argThat(matchingText(transferId)));

		NotificationLog log = awaitLogged(transferId);
		assertThat(log.getType()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
		assertThat(log.getRecipientEmail()).isEqualTo("completed-" + ownerId + "@example.com");
	}

	@Test
	void aFailedTransferEmailsTheOwnerWithTheReasonAndRecordsIt() throws Exception {
		UUID transferId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		UPSTREAM.addAccount(fromAccountId, ownerId);
		UPSTREAM.addUser(ownerId, "failed-" + ownerId + "@example.com", "Bob Customer");

		produce(Topics.TRANSFER_FAILED, transferId, fromAccountId, "FAIL-anything", 20_00L, "PARTNER_REJECTED");

		verify(mailSender, timeout(15_000))
				.send(argThat((SimpleMailMessage m) -> matchingText(transferId).matches(m)
						&& m.getText().contains("PARTNER_REJECTED")));

		NotificationLog log = awaitLogged(transferId);
		assertThat(log.getType()).isEqualTo(NotificationType.TRANSFER_FAILED);
	}

	@Test
	void redeliveringTheSameTransferNeverSendsASecondEmail() throws Exception {
		UUID transferId = UUID.randomUUID();
		UUID fromAccountId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		UPSTREAM.addAccount(fromAccountId, ownerId);
		UPSTREAM.addUser(ownerId, "redelivery-" + ownerId + "@example.com", "Cara Customer");

		produce(Topics.TRANSFER_COMPLETED, transferId, fromAccountId, "PARTNER-1", 1_000L, null);
		awaitLogged(transferId);

		produce(Topics.TRANSFER_COMPLETED, transferId, fromAccountId, "PARTNER-1", 1_000L, null);
		Thread.sleep(1000);

		assertThat(notificationLogs.findAll().stream().filter(n -> n.getTransferId().equals(transferId)))
				.hasSize(1);
		Mockito.verify(mailSender, Mockito.times(1)).send(argThat(matchingText(transferId)));
	}

	@Test
	void aMessageThatCanNeverBeProcessedEndsUpOnTheDeadLetterTopic() throws Exception {
		String marker = "poison-" + UUID.randomUUID();
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
			producer.send(new ProducerRecord<>(Topics.TRANSFER_COMPLETED, "not valid json: " + marker))
					.get(5, TimeUnit.SECONDS);
		}

		List<ConsumerRecord<String, String>> onDlt = consumeMatching(Topics.dlt(Topics.TRANSFER_COMPLETED), marker);
		assertThat(onDlt).hasSize(1);
	}

	private static org.mockito.ArgumentMatcher<SimpleMailMessage> matchingText(UUID transferId) {
		return message -> message != null && message.getText() != null
				&& message.getText().contains(transferId.toString());
	}

	private void produce(String topic, UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
			String reason) throws Exception {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
			TransferResultPayload payload = new TransferResultPayload(transferId, fromAccountId, toAccountId,
					amountMinor, "USD", reason);
			EventEnvelope<TransferResultPayload> envelope = new EventEnvelope<>(UUID.randomUUID(), topic,
					transferId.toString(), Instant.now(), UUID.randomUUID().toString(), payload);
			String json = objectMapper.writeValueAsString(envelope);
			producer.send(new ProducerRecord<>(topic, fromAccountId.toString(), json)).get(5, TimeUnit.SECONDS);
		}
	}

	private NotificationLog awaitLogged(UUID transferId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			var found = notificationLogs.findByTransferId(transferId);
			if (found.isPresent()) {
				return found.get();
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Transfer " + transferId + " was never recorded as notified");
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
