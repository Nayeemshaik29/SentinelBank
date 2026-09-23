package com.sentinelbank.aiagent;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;

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
 * account-service (and, identically, transaction-service) being unreachable when the assistant tries to
 * build its context — a separate context from {@link AiAskControllerTests} because "down from the start"
 * has to be true before the Spring context even comes up. The model is never even asked: there is nothing
 * to answer with.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiAskWhenAccountServiceIsDownTests {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws IOException {
		String closedPortUrl = "http://127.0.0.1:" + findAndCloseAPort();
		registry.add("sentinelbank.services.account", () -> closedPortUrl);
		registry.add("sentinelbank.services.transaction", () -> closedPortUrl);
	}

	private static int findAndCloseAPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void aDownAccountServiceGetsAClearFallbackInsteadOfAnError() throws Exception {
		mvc.perform(post("/ai/ask").header("X-User-Id", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"What's my balance?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value(
						"I couldn't access your account information right now. Please try again in a moment."));
	}
}
