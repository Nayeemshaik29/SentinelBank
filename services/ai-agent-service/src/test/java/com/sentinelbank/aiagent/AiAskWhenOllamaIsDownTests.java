package com.sentinelbank.aiagent;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ollama being unreachable — the exact risk PLAN.md calls out for this day ("make the endpoint work with a
 * stub when Ollama isn't running"). account-service/transaction-service answer normally; only the model
 * call fails. A separate context from {@link AiAskControllerTests}, since "down from the start" has to be
 * true before the Spring context comes up.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiAskWhenOllamaIsDownTests {

	private static final StubAccountAndTransactionServices UPSTREAM = new StubAccountAndTransactionServices();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws IOException {
		registry.add("sentinelbank.services.account", UPSTREAM::url);
		registry.add("sentinelbank.services.transaction", UPSTREAM::url);
		String closedPortUrl = "http://127.0.0.1:" + findAndCloseAPort();
		registry.add("spring.ai.ollama.base-url", () -> closedPortUrl);
	}

	private static int findAndCloseAPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	@AfterAll
	static void closeStub() {
		UPSTREAM.close();
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void aDownOllamaGetsAClearFallbackInsteadOfAnError() throws Exception {
		UUID userId = UUID.randomUUID();
		UPSTREAM.addAccount(userId, "SB1234567890", "USD", 482450, "ACTIVE");

		mvc.perform(post("/ai/ask").header("X-User-Id", userId.toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"What's my balance?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer")
						.value("The assistant is temporarily unavailable. Please try again shortly."));
	}
}
