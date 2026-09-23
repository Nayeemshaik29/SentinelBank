package com.sentinelbank.aiagent;

import java.time.Instant;
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
 * Proves the assistant end to end against fake account-service/transaction-service and a fake Ollama —
 * real network calls, real JSON, no mocking of this service's own beans — covering the two things Day 10's
 * hardening pass established matters most: the happy path actually answers using the caller's own (masked)
 * data, and {@code OutputValidator} genuinely intercepts a hallucinated action claim before it reaches the
 * customer. Degraded-mode behavior (account-service down, Ollama down) is covered by the two other test
 * classes in this package, each with its own context, since the "down" scenario has to be true from the
 * moment the Spring context starts.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiAskControllerTests {

	private static final StubAccountAndTransactionServices UPSTREAM = new StubAccountAndTransactionServices();

	private static final StubOllama OLLAMA = new StubOllama();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("sentinelbank.services.account", UPSTREAM::url);
		registry.add("sentinelbank.services.transaction", UPSTREAM::url);
		registry.add("spring.ai.ollama.base-url", OLLAMA::url);
	}

	@AfterAll
	static void closeStubs() {
		UPSTREAM.close();
		OLLAMA.close();
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void answersUsingTheCallersOwnAccountAndTransferData() throws Exception {
		UUID userId = UUID.randomUUID();
		UPSTREAM.addAccount(userId, "SB1234567890", "USD", 482450, "ACTIVE");
		UPSTREAM.addTransfer(userId, UUID.randomUUID(), "PARTNER-DEMO", "USD", 12550, "COMPLETED", Instant.now());
		OLLAMA.setReply("You have $4,824.50 in your USD account, and your most recent transfer was "
				+ "$125.50 to PARTNER-DEMO, which completed successfully.");

		mvc.perform(post("/ai/ask").header("X-User-Id", userId.toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"What's my balance?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value(
						"You have $4,824.50 in your USD account, and your most recent transfer was "
								+ "$125.50 to PARTNER-DEMO, which completed successfully."));
	}

	@Test
	void aHallucinatedActionClaimIsReplacedWithTheSafeFallbackBeforeReachingTheCustomer() throws Exception {
		UUID userId = UUID.randomUUID();
		UPSTREAM.addAccount(userId, "SB1234567890", "USD", 482450, "ACTIVE");
		OLLAMA.setReply("Sure! I have transferred $500 to your friend as requested.");

		mvc.perform(post("/ai/ask").header("X-User-Id", userId.toString()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"question\":\"Send $500 to my friend\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value(
						"I can only share information about your accounts and transfers — I'm not able to "
								+ "make transfers, change anything, or take any action. Please use the app "
								+ "directly for that."));
	}

	@Test
	void aBlankQuestionIsRejectedByValidation() throws Exception {
		mvc.perform(post("/ai/ask").header("X-User-Id", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
				.andExpect(status().isBadRequest());
	}
}
