package com.sentinelbank.gateway;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Uses tiny limits (3 logins and 5 general requests per minute) so the limit can be reached in a test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRateLimitTests {

	private static final StubDownstream stub = new StubDownstream();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("sentinelbank.jwt.secret", () -> TestTokens.SECRET);
		registry.add("sentinelbank.services.auth", stub::url);
		registry.add("sentinelbank.services.account", stub::url);
		registry.add("sentinelbank.rate-limit.general-per-minute", () -> "5");
		registry.add("sentinelbank.rate-limit.auth-per-minute", () -> "3");
	}

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@AfterAll
	static void stopStub() {
		stub.close();
	}

	@Test
	void loginIsLimitedPerClientAndTellsWhenToRetry() {
		for (int attempt = 1; attempt <= 3; attempt++) {
			client.post().uri("/api/auth/login").bodyValue("{}").exchange().expectStatus().isOk();
		}

		client.post().uri("/api/auth/login").bodyValue("{}").exchange()
				.expectStatus().isEqualTo(429)
				.expectHeader().exists("Retry-After")
				.expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
	}

	@Test
	void theGeneralLimitIsPerUserSoOneNoisyUserDoesNotBlockAnother() {
		String noisy = TestTokens.token("noisy", "n@example.com", List.of("CUSTOMER"));
		String quiet = TestTokens.token("quiet", "q@example.com", List.of("CUSTOMER"));

		for (int request = 1; request <= 5; request++) {
			client.get().uri("/api/accounts/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + noisy).exchange()
					.expectStatus().isOk();
		}
		client.get().uri("/api/accounts/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + noisy).exchange()
				.expectStatus().isEqualTo(429);

		client.get().uri("/api/accounts/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + quiet).exchange()
				.expectStatus().isOk();
	}
}
