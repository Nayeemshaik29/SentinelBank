package com.sentinelbank.aiagent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

/**
 * A tiny fake account-service + transaction-service, on one embedded HTTP server: answers
 * {@code GET /accounts} and {@code GET /transfers} for whichever {@code X-User-Id} the request carries —
 * everything {@link com.sentinelbank.aiagent.service.SpendingContextBuilder} needs, without needing two
 * real Spring contexts. Same technique as every other stubbed cross-service test in this project.
 */
final class StubAccountAndTransactionServices implements AutoCloseable {

	private record AccountFixture(String accountNumber, String currency, long balanceMinor, String status) {
	}

	private record TransferFixture(UUID id, String toAccountId, String currency, long amountMinor, String status,
			Instant createdAt) {
	}

	private final HttpServer server;

	// Every timestamp in the fixture bodies is already a String (see handleAccounts/handleTransfers), so
	// this needs no java.time module — Jackson 3's databind (this module's default, since it is not one
	// of the Kafka-consuming services that build their own Jackson 2 ObjectMapper) has no jsr310 module.
	private final ObjectMapper mapper = new ObjectMapper();

	private final Map<UUID, List<AccountFixture>> accountsByOwner = new ConcurrentHashMap<>();

	private final Map<UUID, List<TransferFixture>> transfersByOwner = new ConcurrentHashMap<>();

	StubAccountAndTransactionServices() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/accounts", this::handleAccounts);
		server.createContext("/transfers", this::handleTransfers);
		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void addAccount(UUID ownerId, String accountNumber, String currency, long balanceMinor, String status) {
		accountsByOwner.computeIfAbsent(ownerId, key -> new ArrayList<>())
				.add(new AccountFixture(accountNumber, currency, balanceMinor, status));
	}

	void addTransfer(UUID ownerId, UUID transferId, String toAccountId, String currency, long amountMinor,
			String status, Instant createdAt) {
		transfersByOwner.computeIfAbsent(ownerId, key -> new ArrayList<>())
				.add(new TransferFixture(transferId, toAccountId, currency, amountMinor, status, createdAt));
	}

	private void handleAccounts(HttpExchange exchange) throws IOException {
		UUID ownerId = requireUserId(exchange);
		List<Map<String, Object>> body = new ArrayList<>();
		for (AccountFixture fixture : accountsByOwner.getOrDefault(ownerId, List.of())) {
			Map<String, Object> account = new LinkedHashMap<>();
			account.put("id", UUID.randomUUID().toString());
			account.put("ownerId", ownerId.toString());
			account.put("accountNumber", fixture.accountNumber());
			account.put("currency", fixture.currency());
			account.put("balanceMinor", fixture.balanceMinor());
			account.put("status", fixture.status());
			account.put("createdAt", Instant.now().toString());
			body.add(account);
		}
		sendJson(exchange, 200, body);
	}

	private void handleTransfers(HttpExchange exchange) throws IOException {
		UUID ownerId = requireUserId(exchange);
		List<Map<String, Object>> body = new ArrayList<>();
		for (TransferFixture fixture : transfersByOwner.getOrDefault(ownerId, List.of())) {
			Map<String, Object> transfer = new LinkedHashMap<>();
			transfer.put("id", fixture.id().toString());
			transfer.put("ownerId", ownerId.toString());
			transfer.put("fromAccountId", UUID.randomUUID().toString());
			transfer.put("toAccountId", fixture.toAccountId());
			transfer.put("currency", fixture.currency());
			transfer.put("amountMinor", fixture.amountMinor());
			transfer.put("status", fixture.status());
			transfer.put("failureCode", null);
			transfer.put("failureDetail", null);
			transfer.put("createdAt", fixture.createdAt().toString());
			transfer.put("updatedAt", fixture.createdAt().toString());
			body.add(transfer);
		}
		sendJson(exchange, 200, body);
	}

	private static UUID requireUserId(HttpExchange exchange) {
		String header = exchange.getRequestHeaders().getFirst("X-User-Id");
		return UUID.fromString(header);
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
