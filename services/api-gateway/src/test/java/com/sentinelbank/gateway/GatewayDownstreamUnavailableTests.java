package com.sentinelbank.gateway;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The fraud service points at a port where nothing listens, as if that service had crashed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayDownstreamUnavailableTests {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("sentinelbank.jwt.secret", () -> TestTokens.SECRET);
		registry.add("sentinelbank.services.fraud", () -> "http://localhost:" + closedPort());
	}

	/** A port that was free a moment ago and has nothing listening on it: connections are refused. */
	private static int closedPort() {
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void aServiceThatIsDownAnswers503NotAGeneric500() {
		String analyst = TestTokens.token("a-1", "a@example.com", List.of("ANALYST"));

		client.get().uri("/api/fraud/cases").header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst).exchange()
				.expectStatus().isEqualTo(503)
				.expectHeader().exists("X-Correlation-Id")
				.expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
	}
}
