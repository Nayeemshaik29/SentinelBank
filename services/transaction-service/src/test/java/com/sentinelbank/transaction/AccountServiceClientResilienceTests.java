package com.sentinelbank.transaction;

import java.util.UUID;

import com.sentinelbank.transaction.client.AccountServiceClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Day 10 hardening: every other test that exercises {@link AccountServiceClient} against a failing
 * account-service only ever proves the {@code @Retry} half of its resilience config (see
 * {@code accountServiceErroringOnDebitIsRetriedThenFailsCleanly} in {@link TransferApiTests}) — a single
 * request, retried a few times, then a clean failure. Nothing until now proved the other half: that enough
 * consecutive failures actually **open the circuit breaker**, so a struggling account-service stops being
 * hammered at all, and that it **recovers** once account-service is healthy again.
 *
 * <p>The real {@code sliding-window-size: 20} / {@code minimum-number-of-calls: 10} from application.yaml
 * would make this deterministic but slow (and slower still since {@code @Retry} wraps
 * {@code @CircuitBreaker} here — resilience4j's default aspect order — so every failing call already
 * contributes up to 3 attempts to the breaker's window). A small window, overridden just for this test, is
 * what makes "trip it, then watch it recover" fit in a few seconds without weakening what the test proves.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountServiceClientResilienceTests {

	private static StubAccountService stubAccountService;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		stubAccountService = new StubAccountService();
		registry.add("sentinelbank.services.account-url", stubAccountService::url);
		registry.add("resilience4j.circuitbreaker.instances.account-service.sliding-window-size", () -> "4");
		registry.add("resilience4j.circuitbreaker.instances.account-service.minimum-number-of-calls", () -> "4");
		registry.add("resilience4j.circuitbreaker.instances.account-service.wait-duration-in-open-state", () -> "1s");
		registry.add(
				"resilience4j.circuitbreaker.instances.account-service.permitted-number-of-calls-in-half-open-state",
				() -> "1");
	}

	@AfterAll
	static void closeStub() {
		stubAccountService.close();
	}

	@Autowired
	private AccountServiceClient client;

	@Test
	void repeatedFailuresOpenTheCircuitThenItRecoversOnceAccountServiceIsHealthyAgain() throws Exception {
		UUID accountId = UUID.randomUUID();
		stubAccountService.addAccount(accountId, UUID.randomUUID(), "USD");
		stubAccountService.setDebitBehavior(StubAccountService.DebitBehavior.SERVER_ERROR);

		// A handful of failing calls — comfortably more than minimum-number-of-calls even accounting for
		// @Retry's own attempts each contributing to the breaker's window — to guarantee it trips.
		for (int i = 0; i < 4; i++) {
			String referenceId = "cb-warmup-" + i;
			assertThatThrownBy(() -> client.debit(accountId, referenceId, 100, "warm-up"));
		}

		int callsBeforeOpen = stubAccountService.totalDebitCalls();

		// Open now: the very next call must fail immediately as a rejection, not another server error —
		// and, the real proof, must never reach account-service at all.
		assertThatThrownBy(() -> client.debit(accountId, "cb-open-probe", 100, "probe"))
				.isInstanceOf(CallNotPermittedException.class);
		assertThat(stubAccountService.totalDebitCalls())
				.as("a call while the breaker is open must be rejected locally, not sent over the network")
				.isEqualTo(callsBeforeOpen);

		// Account-service recovers; once wait-duration-in-open-state has passed, the breaker allows a
		// trial call through (half-open) and, since it succeeds, closes again.
		stubAccountService.setDebitBehavior(StubAccountService.DebitBehavior.SUCCEED);
		Thread.sleep(1_200);

		client.debit(accountId, "cb-recovered", 100, "recovered"); // must not throw
		assertThat(stubAccountService.debitCallsFor("cb-recovered")).isEqualTo(1);
	}
}
