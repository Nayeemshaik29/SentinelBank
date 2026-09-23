package com.sentinelbank.notification;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny fake account-service + auth-service, on one embedded HTTP server: answers
 * {@code GET /internal/accounts/{id}} with a fixture's {@code ownerId}, and {@code GET /internal/users/{id}}
 * with a fixture's email/name — everything {@link com.sentinelbank.notification.service.TransferNotificationService}
 * needs to resolve a recipient, without needing two second full Spring contexts. See transaction-service's
 * {@code StubAccountService} for the same technique.
 */
final class StubUpstreamServices implements AutoCloseable {

	private record UserFixture(String email, String fullName) {
	}

	private final HttpServer server;

	private final ObjectMapper mapper = new ObjectMapper();

	private final Map<UUID, UUID> accountOwners = new ConcurrentHashMap<>();

	private final Map<UUID, UserFixture> users = new ConcurrentHashMap<>();

	StubUpstreamServices() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/internal/accounts/", this::handleGetAccount);
		server.createContext("/internal/users/", this::handleGetUser);
		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void addAccount(UUID accountId, UUID ownerId) {
		accountOwners.put(accountId, ownerId);
	}

	void addUser(UUID userId, String email, String fullName) {
		users.put(userId, new UserFixture(email, fullName));
	}

	private void handleGetAccount(HttpExchange exchange) throws IOException {
		UUID accountId = idFromPath(exchange.getRequestURI().getPath(), "/internal/accounts/");
		UUID ownerId = accountOwners.get(accountId);
		if (ownerId == null) {
			sendJson(exchange, 404, Map.of("code", "ACCOUNT_NOT_FOUND", "detail", "Account not found"));
			return;
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", accountId.toString());
		body.put("ownerId", ownerId.toString());
		body.put("accountNumber", "SB0000000099");
		body.put("currency", "USD");
		body.put("balanceMinor", 0);
		body.put("status", "ACTIVE");
		sendJson(exchange, 200, body);
	}

	private void handleGetUser(HttpExchange exchange) throws IOException {
		UUID userId = idFromPath(exchange.getRequestURI().getPath(), "/internal/users/");
		UserFixture fixture = users.get(userId);
		if (fixture == null) {
			sendJson(exchange, 404, Map.of("code", "USER_NOT_FOUND", "detail", "User not found"));
			return;
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", userId.toString());
		body.put("email", fixture.email());
		body.put("fullName", fixture.fullName());
		body.put("role", "CUSTOMER");
		body.put("kycStatus", "VERIFIED");
		body.put("createdAt", "2024-01-01T00:00:00Z");
		sendJson(exchange, 200, body);
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
