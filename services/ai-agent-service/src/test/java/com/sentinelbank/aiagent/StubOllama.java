package com.sentinelbank.aiagent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

/**
 * A fake Ollama server: answers {@code POST /api/chat} with a settable canned reply, in the exact
 * non-streaming response shape the real Ollama returns (confirmed directly against a real local Ollama
 * before writing this). Lets the assistant's happy path and its output-validator path both be tested
 * fast and deterministically, without needing a real model loaded.
 */
final class StubOllama implements AutoCloseable {

	private final HttpServer server;

	private final ObjectMapper mapper = new ObjectMapper();

	private volatile String reply = "Your balance looks healthy.";

	private final AtomicInteger totalChatCalls = new AtomicInteger();

	StubOllama() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/api/chat", this::handleChat);
		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void setReply(String reply) {
		this.reply = reply;
	}

	int totalChatCalls() {
		return totalChatCalls.get();
	}

	private void handleChat(HttpExchange exchange) throws IOException {
		totalChatCalls.incrementAndGet();
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "assistant");
		message.put("content", reply);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", "llama3");
		body.put("created_at", java.time.Instant.now().toString());
		body.put("message", message);
		body.put("done", true);
		body.put("done_reason", "stop");

		byte[] bytes = mapper.writeValueAsBytes(body);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
