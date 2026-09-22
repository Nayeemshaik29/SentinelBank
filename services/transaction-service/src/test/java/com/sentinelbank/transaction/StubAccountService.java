package com.sentinelbank.transaction;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny fake account-service. It answers GET /accounts/{id} from a small set of fixtures added by the
 * test, and POST /internal/accounts/{id}/debit according to a switchable behaviour, so a test can drive
 * exactly the scenario it wants (success, insufficient funds, or account-service itself erroring) without
 * needing a second full Spring context.
 */
final class StubAccountService implements AutoCloseable {

	enum DebitBehavior {
		SUCCEED,
		INSUFFICIENT_FUNDS,
		SERVER_ERROR
	}

	private record AccountFixture(UUID ownerId, String currency) {
	}

	private final HttpServer server;

	private final ObjectMapper mapper = new ObjectMapper();

	private final Map<UUID, AccountFixture> accounts = new ConcurrentHashMap<>();

	private volatile DebitBehavior debitBehavior = DebitBehavior.SUCCEED;

	private final AtomicInteger debitCallCount = new AtomicInteger();

	StubAccountService() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/accounts/", this::handleGetAccount);
		server.createContext("/internal/accounts/", this::handleDebit);
		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void addAccount(UUID accountId, UUID ownerId, String currency) {
		accounts.put(accountId, new AccountFixture(ownerId, currency));
	}

	void setDebitBehavior(DebitBehavior behavior) {
		this.debitBehavior = behavior;
	}

	int debitCallCount() {
		return debitCallCount.get();
	}

	private void handleGetAccount(HttpExchange exchange) throws IOException {
		UUID accountId = idFromPath(exchange.getRequestURI().getPath(), "/accounts/");
		AccountFixture fixture = accounts.get(accountId);
		if (fixture == null) {
			sendJson(exchange, 404, Map.of("code", "ACCOUNT_NOT_FOUND", "detail", "Account not found"));
			return;
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", accountId.toString());
		body.put("ownerId", fixture.ownerId().toString());
		body.put("accountNumber", "SB0000000099");
		body.put("currency", fixture.currency());
		body.put("balanceMinor", 1_000_000);
		body.put("status", "ACTIVE");
		sendJson(exchange, 200, body);
	}

	private void handleDebit(HttpExchange exchange) throws IOException {
		debitCallCount.incrementAndGet();
		Map<?, ?> request = mapper.readValue(exchange.getRequestBody(), Map.class);
		String referenceId = String.valueOf(request.get("referenceId"));
		switch (debitBehavior) {
			case INSUFFICIENT_FUNDS ->
				sendJson(exchange, 409, Map.of("code", "INSUFFICIENT_FUNDS", "detail", "Insufficient funds"));
			case SERVER_ERROR -> sendJson(exchange, 500, Map.of("code", "INTERNAL_ERROR", "detail", "boom"));
			case SUCCEED -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", UUID.randomUUID().toString());
				body.put("entryType", "DEBIT");
				body.put("amountMinor", request.get("amountMinor"));
				body.put("balanceAfter", 0);
				body.put("referenceId", referenceId);
				body.put("description", request.get("description"));
				body.put("createdAt", "2024-01-01T00:00:00Z");
				sendJson(exchange, 200, body);
			}
		}
	}

	private static UUID idFromPath(String path, String prefix) {
		String rest = path.substring(prefix.length());
		int slash = rest.indexOf('/');
		return UUID.fromString(slash < 0 ? rest : rest.substring(0, slash));
	}

	private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
		byte[] bytes = mapper.writeValueAsBytes(body);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
