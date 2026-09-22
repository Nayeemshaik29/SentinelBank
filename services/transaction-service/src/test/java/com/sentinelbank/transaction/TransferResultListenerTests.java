package com.sentinelbank.transaction;

import java.time.Instant;
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
import com.sentinelbank.transaction.kafka.TransferResultPayload;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The other half of the saga (Day 6), proven end to end against a real Postgres and a real Kafka: a real
 * transfer is created through the API (Day 4's flow, DEBITED), then a {@code transfer.completed} or
 * {@code transfer.failed} event — exactly what partner-bank-service would publish — is produced directly
 * to Kafka, and the consumer is proven to complete or compensate it correctly, including on redelivery.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferResultListenerTests {

	private static StubAccountService stubAccountService;

	private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		stubAccountService = new StubAccountService();
		registry.add("sentinelbank.services.account-url", stubAccountService::url);
		KAFKA.start();
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
	}

	@AfterAll
	static void tearDown() {
		stubAccountService.close();
		KAFKA.stop();
	}

	@Autowired
	private MockMvc mvc;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	void resetStub() {
		stubAccountService.setDebitBehavior(StubAccountService.DebitBehavior.SUCCEED);
	}

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
	void consumingTransferCompletedMovesTheTransferToCompleted() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		String transferId = createDebitedTransfer(ownerId, fromAccountId, "PARTNER-X", 1000L);

		produceResult(Topics.TRANSFER_COMPLETED, UUID.fromString(transferId), fromAccountId, "PARTNER-X", 1000L,
				null);

		awaitStatus(transferId, ownerId, "COMPLETED");
	}

	@Test
	void consumingTransferFailedCompensatesByCreditingTheMoneyBackAndReversesTheTransfer() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		String transferId = createDebitedTransfer(ownerId, fromAccountId, "FAIL-X", 1500L);

		produceResult(Topics.TRANSFER_FAILED, UUID.fromString(transferId), fromAccountId, "FAIL-X", 1500L,
				"Partner bank rejected the credit");

		awaitStatus(transferId, ownerId, "REVERSED");
		assertThat(stubAccountService.creditCallsFor(transferId)).as("the debit was refunded exactly once")
				.isEqualTo(1);
	}

	@Test
	void redeliveringTransferCompletedIsIdempotent() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		String transferId = createDebitedTransfer(ownerId, fromAccountId, "PARTNER-Y", 250L);

		produceResult(Topics.TRANSFER_COMPLETED, UUID.fromString(transferId), fromAccountId, "PARTNER-Y", 250L,
				null);
		awaitStatus(transferId, ownerId, "COMPLETED");

		// a genuine Kafka redelivery of the same event: must not error and must not change anything
		produceResult(Topics.TRANSFER_COMPLETED, UUID.fromString(transferId), fromAccountId, "PARTNER-Y", 250L,
				null);
		Thread.sleep(1000);
		awaitStatus(transferId, ownerId, "COMPLETED");
	}

	@Test
	void redeliveringTransferFailedNeverCreditsTheAccountTwice() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		String transferId = createDebitedTransfer(ownerId, fromAccountId, "FAIL-Y", 400L);

		produceResult(Topics.TRANSFER_FAILED, UUID.fromString(transferId), fromAccountId, "FAIL-Y", 400L, "rejected");
		awaitStatus(transferId, ownerId, "REVERSED");

		produceResult(Topics.TRANSFER_FAILED, UUID.fromString(transferId), fromAccountId, "FAIL-Y", 400L, "rejected");
		Thread.sleep(1000);
		awaitStatus(transferId, ownerId, "REVERSED");
		assertThat(stubAccountService.creditCallsFor(transferId)).as("redelivery never refunds twice").isEqualTo(1);
	}

	private UUID registerAccount(String ownerId) {
		UUID accountId = UUID.randomUUID();
		stubAccountService.addAccount(accountId, UUID.fromString(ownerId), "USD");
		return accountId;
	}

	private String createDebitedTransfer(String ownerId, UUID fromAccountId, String toAccountId, long amountMinor)
			throws Exception {
		String idempotencyKey = "result-test-" + UUID.randomUUID();
		String body = mvc.perform(post("/transfers").header("X-User-Id", ownerId)
				.header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromAccountId\":\"" + fromAccountId + "\",\"toAccountId\":\"" + toAccountId
						+ "\",\"amountMinor\":" + amountMinor + "}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("DEBITED");
		return JsonPath.read(body, "$.id");
	}

	private void produceResult(String eventType, UUID transferId, UUID fromAccountId, String toAccountId,
			long amountMinor, String reason) throws Exception {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
			TransferResultPayload payload = new TransferResultPayload(transferId, fromAccountId, toAccountId,
					amountMinor, "USD", reason);
			EventEnvelope<TransferResultPayload> envelope = new EventEnvelope<>(UUID.randomUUID(), eventType,
					transferId.toString(), Instant.now(), UUID.randomUUID().toString(), payload);
			String json = objectMapper.writeValueAsString(envelope);
			producer.send(new ProducerRecord<>(eventType, fromAccountId.toString(), json)).get(5, TimeUnit.SECONDS);
		}
	}

	private void awaitStatus(String transferId, String ownerId, String expectedStatus) throws Exception {
		long deadline = System.currentTimeMillis() + 15_000;
		String lastSeen = null;
		while (System.currentTimeMillis() < deadline) {
			String body = mvc.perform(get("/transfers/" + transferId).header("X-User-Id", ownerId))
					.andReturn().getResponse().getContentAsString();
			lastSeen = JsonPath.read(body, "$.status");
			if (expectedStatus.equals(lastSeen)) {
				return;
			}
			Thread.sleep(200);
		}
		fail("Transfer " + transferId + " never reached status " + expectedStatus + "; last seen: " + lastSeen);
	}
}
