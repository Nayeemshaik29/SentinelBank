package com.sentinelbank.transaction;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferApiTests {

	private static StubAccountService stubAccountService;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		stubAccountService = new StubAccountService();
		registry.add("sentinelbank.services.account-url", stubAccountService::url);
	}

	@Autowired
	private MockMvc mvc;

	@BeforeEach
	void resetStub() {
		stubAccountService.setDebitBehavior(StubAccountService.DebitBehavior.SUCCEED);
	}

	@AfterAll
	static void closeStub() {
		stubAccountService.close();
	}

	private UUID registerAccount(String ownerId) {
		UUID accountId = UUID.randomUUID();
		stubAccountService.addAccount(accountId, UUID.fromString(ownerId), "USD");
		return accountId;
	}

	private MvcResult postTransfer(String ownerId, String idempotencyKey, UUID fromAccountId, String toAccountId,
			long amountMinor) throws Exception {
		String body = """
				{"fromAccountId":"%s","toAccountId":"%s","amountMinor":%d}
				""".formatted(fromAccountId, toAccountId, amountMinor);
		return mvc.perform(post("/transfers").header("X-User-Id", ownerId)
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn();
	}

	@Test
	void aSuccessfulTransferIsCreatedAndDebitsExactlyOnce() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);

		MvcResult result = postTransfer(ownerId, "key-1", fromAccountId, "PARTNER-ACC-1", 2_500L);

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		String body = result.getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("DEBITED");
		assertThat((String) JsonPath.read(body, "$.currency")).isEqualTo("USD");
		assertThat(stubAccountService.debitCallCount()).isEqualTo(1);
	}

	@Test
	void repeatingTheSameIdempotencyKeyReturnsTheSameTransferAndDebitsOnlyOnce() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);

		MvcResult first = postTransfer(ownerId, "key-repeat", fromAccountId, "PARTNER-ACC-1", 1_000L);
		MvcResult second = postTransfer(ownerId, "key-repeat", fromAccountId, "PARTNER-ACC-1", 1_000L);

		assertThat(first.getResponse().getStatus()).isEqualTo(201);
		assertThat(second.getResponse().getStatus()).isEqualTo(200);
		String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
		String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.id");
		assertThat(secondId).isEqualTo(firstId);
		assertThat(stubAccountService.debitCallCount()).as("the debit only ever happened once").isEqualTo(1);
	}

	@Test
	void reusingAnIdempotencyKeyWithADifferentRequestIsRejected() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		postTransfer(ownerId, "key-mismatch", fromAccountId, "PARTNER-ACC-1", 1_000L);

		MvcResult second = postTransfer(ownerId, "key-mismatch", fromAccountId, "PARTNER-ACC-2", 5_000L);

		assertThat(second.getResponse().getStatus()).isEqualTo(409);
		assertThat((String) JsonPath.read(second.getResponse().getContentAsString(), "$.code"))
				.isEqualTo("IDEMPOTENCY_KEY_REUSED");
	}

	@Test
	void aMissingIdempotencyKeyHeaderIsRejected() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);

		mvc.perform(post("/transfers").header("X-User-Id", ownerId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromAccountId\":\"" + fromAccountId + "\",\"toAccountId\":\"X\",\"amountMinor\":100}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aNonPositiveAmountIsRejectedByValidation() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);

		mvc.perform(post("/transfers").header("X-User-Id", ownerId).header("Idempotency-Key", "key-zero")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromAccountId\":\"" + fromAccountId + "\",\"toAccountId\":\"X\",\"amountMinor\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		assertThat(stubAccountService.debitCallCount()).isZero();
	}

	@Test
	void anUnknownOrNotOwnedAccountIsRejectedBeforeAnyTransferIsCreated() throws Exception {
		String ownerId = UUID.randomUUID().toString();

		MvcResult result = postTransfer(ownerId, "key-unknown", UUID.randomUUID(), "PARTNER-ACC-1", 100L);

		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.code"))
				.isEqualTo("ACCOUNT_NOT_FOUND");
		assertThat(stubAccountService.debitCallCount()).isZero();
		mvc.perform(get("/transfers").header("X-User-Id", ownerId)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void insufficientFundsAtDebitTimeStillCreatesADurableFailedRecord() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		stubAccountService.setDebitBehavior(StubAccountService.DebitBehavior.INSUFFICIENT_FUNDS);

		MvcResult result = postTransfer(ownerId, "key-insufficient", fromAccountId, "PARTNER-ACC-1", 999_999L);

		assertThat(result.getResponse().getStatus()).as("a transfer resource was created, even though it failed")
				.isEqualTo(201);
		String body = result.getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("FAILED");
		assertThat((String) JsonPath.read(body, "$.failureCode")).isEqualTo("INSUFFICIENT_FUNDS");

		String transferId = JsonPath.read(body, "$.id");
		mvc.perform(get("/transfers/" + transferId).header("X-User-Id", ownerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));
	}

	@Test
	void accountServiceErroringOnDebitIsRetriedThenFailsCleanly() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		stubAccountService.setDebitBehavior(StubAccountService.DebitBehavior.SERVER_ERROR);

		MvcResult result = postTransfer(ownerId, "key-server-error", fromAccountId, "PARTNER-ACC-1", 100L);

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		String body = result.getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("FAILED");
		assertThat((String) JsonPath.read(body, "$.failureCode")).isEqualTo("ACCOUNT_SERVICE_UNAVAILABLE");
		assertThat(stubAccountService.debitCallCount()).as("a 5xx is retried (max-attempts: 3 in application.yaml)")
				.isEqualTo(3);
	}

	@Test
	void anAnalystCanViewAnyonesTransferButAnotherCustomerCannot() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		String transferId = JsonPath.read(postTransfer(ownerId, "key-visibility", fromAccountId, "PARTNER-ACC-1",
				100L).getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/transfers/" + transferId).header("X-User-Id", UUID.randomUUID().toString()))
				.andExpect(status().isNotFound());
		mvc.perform(get("/transfers/" + transferId).header("X-User-Id", UUID.randomUUID().toString())
				.header("X-User-Roles", "ANALYST"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(transferId));
	}

	@Test
	void concurrentRepeatsOfTheSameIdempotencyKeyStillDebitOnlyOnce() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		UUID fromAccountId = registerAccount(ownerId);
		int callers = 12;
		CyclicBarrier barrier = new CyclicBarrier(callers);
		ExecutorService pool = Executors.newFixedThreadPool(callers);
		try {
			var futures = IntStream.range(0, callers).<Callable<Integer>>mapToObj(i -> () -> {
				barrier.await();
				return postTransfer(ownerId, "key-concurrent", fromAccountId, "PARTNER-ACC-1", 500L)
						.getResponse().getStatus();
			}).map(pool::submit).toList();
			for (Future<Integer> future : futures) {
				assertThat(future.get(30, TimeUnit.SECONDS)).isIn(200, 201);
			}
		}
		finally {
			pool.shutdown();
		}

		assertThat(stubAccountService.debitCallCount())
				.as("%d simultaneous callers using the same idempotency key still debit once", callers)
				.isEqualTo(1);
	}
}
