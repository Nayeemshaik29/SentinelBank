package com.sentinelbank.notification;

import java.util.UUID;

import com.sentinelbank.notification.client.AccountServiceClient;
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
 * Day 10 hardening. Same proof as transaction-service's {@code AccountServiceClientResilienceTests} — see
 * that class for the full reasoning — applied to notification-service's own account-service client, which
 * had no resilience test at all until now: a struggling account-service trips the breaker, calls stop
 * reaching it entirely while it's open, and it recovers once account-service is healthy again. The
 * {@code sliding-window-size}/{@code minimum-number-of-calls} overrides exist for the same reason: the real
 * values from application.yaml are sized for production traffic, not for tripping the breaker
 * deterministically in a handful of calls.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountServiceClientResilienceTests {

	private static final StubUpstreamServices UPSTREAM = new StubUpstreamServices();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("sentinelbank.services.account", UPSTREAM::url);
		registry.add("sentinelbank.services.auth", UPSTREAM::url);
		registry.add("resilience4j.circuitbreaker.instances.account-service.sliding-window-size", () -> "4");
		registry.add("resilience4j.circuitbreaker.instances.account-service.minimum-number-of-calls", () -> "4");
		registry.add("resilience4j.circuitbreaker.instances.account-service.wait-duration-in-open-state", () -> "1s");
		registry.add(
				"resilience4j.circuitbreaker.instances.account-service.permitted-number-of-calls-in-half-open-state",
				() -> "1");
	}

	@AfterAll
	static void closeStub() {
		UPSTREAM.close();
	}

	@Autowired
	private AccountServiceClient client;

	@Test
	void repeatedFailuresOpenTheCircuitThenItRecoversOnceAccountServiceIsHealthyAgain() throws Exception {
		UUID accountId = UUID.randomUUID();
		UPSTREAM.addAccount(accountId, UUID.randomUUID());
		UPSTREAM.setAccountBehavior(StubUpstreamServices.AccountBehavior.SERVER_ERROR);

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> client.getAccount(accountId));
		}

		int callsBeforeOpen = UPSTREAM.totalAccountCalls();

		assertThatThrownBy(() -> client.getAccount(accountId)).isInstanceOf(CallNotPermittedException.class);
		assertThat(UPSTREAM.totalAccountCalls())
				.as("a call while the breaker is open must be rejected locally, not sent over the network")
				.isEqualTo(callsBeforeOpen);

		UPSTREAM.setAccountBehavior(StubUpstreamServices.AccountBehavior.SUCCEED);
		Thread.sleep(1_200);

		assertThat(client.getAccount(accountId).ownerId()).isNotNull(); // must not throw
	}
}
