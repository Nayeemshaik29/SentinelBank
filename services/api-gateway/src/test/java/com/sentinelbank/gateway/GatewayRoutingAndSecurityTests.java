package com.sentinelbank.gateway;

import java.time.Duration;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingAndSecurityTests {

	private static final StubDownstream stub = new StubDownstream();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("sentinelbank.jwt.secret", () -> TestTokens.SECRET);
		registry.add("sentinelbank.services.auth", stub::url);
		registry.add("sentinelbank.services.account", stub::url);
		registry.add("sentinelbank.services.transaction", stub::url);
		registry.add("sentinelbank.services.fraud", stub::url);
		registry.add("sentinelbank.services.audit", stub::url);
		registry.add("sentinelbank.services.ai-agent", stub::url);
		registry.add("sentinelbank.rate-limit.general-per-minute", () -> "1000");
		registry.add("sentinelbank.rate-limit.auth-per-minute", () -> "1000");
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
	void protectedRouteWithoutATokenIsUnauthorizedAndStillCarriesACorrelationId() {
		client.get().uri("/api/accounts/me").exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().exists("X-Correlation-Id")
				.expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
	}

	@Test
	void publicLoginRouteIsForwardedWithThePrefixStripped() {
		client.post().uri("/api/auth/login").bodyValue("{}").exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.path").isEqualTo("/auth/login");
	}

	@Test
	void aValidTokenReachesTheServiceAsIdentityHeaders() {
		String token = TestTokens.token("user-1", "a@example.com", List.of("CUSTOMER"));

		client.get().uri("/api/accounts/123").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.path").isEqualTo("/accounts/123")
				.jsonPath("$.userId").isEqualTo("user-1")
				.jsonPath("$.email").isEqualTo("a@example.com")
				.jsonPath("$.roles").isEqualTo("CUSTOMER");
	}

	@Test
	void clientsCannotSpoofIdentityHeaders() {
		String token = TestTokens.token("user-1", "a@example.com", List.of("CUSTOMER"));

		// a signed-in customer pretending to be somebody else and an analyst
		client.get().uri("/api/accounts/123")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header("X-User-Id", "hacker")
				.header("X-User-Roles", "ANALYST")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.userId").isEqualTo("user-1")
				.jsonPath("$.roles").isEqualTo("CUSTOMER");

		// an anonymous caller on a public route must not get an identity at all
		client.post().uri("/api/auth/login").header("X-User-Id", "hacker").header("X-User-Roles", "ANALYST")
				.bodyValue("{}").exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.userId").doesNotExist()
				.jsonPath("$.roles").doesNotExist();
	}

	@Test
	void fraudRoutesAreForAnalystsOnly() {
		String customer = TestTokens.token("c-1", "c@example.com", List.of("CUSTOMER"));
		String analyst = TestTokens.token("a-1", "a@example.com", List.of("ANALYST"));

		client.get().uri("/api/fraud/cases").header(HttpHeaders.AUTHORIZATION, "Bearer " + customer).exchange()
				.expectStatus().isForbidden()
				.expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
		client.get().uri("/api/fraud/cases").header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.path").isEqualTo("/fraud/cases");
	}

	@Test
	void auditRoutesAreForAnalystsOnly() {
		String customer = TestTokens.token("c-1", "c@example.com", List.of("CUSTOMER"));
		String analyst = TestTokens.token("a-1", "a@example.com", List.of("ANALYST"));

		client.get().uri("/api/audit/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + customer).exchange()
				.expectStatus().isForbidden()
				.expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
		client.get().uri("/api/audit/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.path").isEqualTo("/audit/events");
	}

	@Test
	void analystsCannotMakeTransfersOrUseTheAiAssistant() {
		String analyst = TestTokens.token("a-1", "a@example.com", List.of("ANALYST"));

		client.post().uri("/api/transfers").header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst)
				.bodyValue("{}").exchange().expectStatus().isForbidden();
		client.post().uri("/api/ai/ask").header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst)
				.bodyValue("{}").exchange().expectStatus().isForbidden();
	}

	@Test
	void customersCanReachTransfers() {
		String customer = TestTokens.token("c-1", "c@example.com", List.of("CUSTOMER"));

		client.post().uri("/api/transfers").header(HttpHeaders.AUTHORIZATION, "Bearer " + customer)
				.bodyValue("{}").exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.path").isEqualTo("/transfers");
	}

	@Test
	void anAnalystCanReadTransfersButNotCreateThem() {
		String analyst = TestTokens.token("a-1", "a@example.com", List.of("ANALYST"));

		client.get().uri("/api/transfers/123").header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.path").isEqualTo("/transfers/123");
		client.post().uri("/api/transfers").header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst)
				.bodyValue("{}").exchange().expectStatus().isForbidden();
	}

	@Test
	void expiredForgedAndGarbageTokensAreAllRejected() {
		String expired = TestTokens.token("u", "u@example.com", List.of("CUSTOMER"), Duration.ofHours(-1),
				TestTokens.SECRET);
		String forged = TestTokens.token("u", "u@example.com", List.of("ANALYST"), Duration.ofMinutes(15),
				"another-secret-another-secret-another-secret");

		for (String bad : List.of(expired, forged, "not-a-jwt")) {
			client.get().uri("/api/accounts/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + bad).exchange()
					.expectStatus().isUnauthorized()
					.expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
		}
	}

	@Test
	void aCallersCorrelationIdIsKeptAndForwardedToTheService() {
		client.post().uri("/api/auth/login").header("X-Correlation-Id", "trace-me-42").bodyValue("{}").exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals("X-Correlation-Id", "trace-me-42")
				.expectBody().jsonPath("$.correlationId").isEqualTo("trace-me-42");
	}

	@Test
	void aCorrelationIdIsCreatedWhenTheCallerSendsNone() {
		byte[] body = client.post().uri("/api/auth/login").bodyValue("{}").exchange()
				.expectStatus().isOk()
				.expectHeader().exists("X-Correlation-Id")
				.expectBody().returnResult().getResponseBody();

		org.assertj.core.api.Assertions.assertThat(new String(body)).contains("\"correlationId\":\"");
	}

	@Test
	void corsPreflightAllowsTheAngularDevServerAndNothingElse() {
		client.options().uri("/api/transfers")
				.header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "authorization,idempotency-key")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200");

		client.options().uri("/api/transfers")
				.header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "POST")
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void healthEndpointIsPublic() {
		client.get().uri("/actuator/health").exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.status").isEqualTo("UP");
	}
}
