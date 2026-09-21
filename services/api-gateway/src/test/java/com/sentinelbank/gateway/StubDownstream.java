package com.sentinelbank.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny fake downstream service. It answers every request with JSON describing what it received (the path
 * and the identity and correlation headers), so tests can see exactly what the gateway forwarded.
 */
final class StubDownstream implements AutoCloseable {

	private final HttpServer server;

	StubDownstream() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/", this::echo);
		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private void echo(HttpExchange exchange) throws IOException {
		List<String> fields = new ArrayList<>();
		fields.add(field("path", exchange.getRequestURI().getPath()));
		addIfPresent(fields, "userId", exchange.getRequestHeaders().getFirst("X-User-Id"));
		addIfPresent(fields, "email", exchange.getRequestHeaders().getFirst("X-User-Email"));
		addIfPresent(fields, "roles", exchange.getRequestHeaders().getFirst("X-User-Roles"));
		addIfPresent(fields, "correlationId", exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
		byte[] body = ("{" + String.join(",", fields) + "}").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private static void addIfPresent(List<String> fields, String name, String value) {
		if (value != null) {
			fields.add(field(name, value));
		}
	}

	private static String field(String name, String value) {
		return "\"" + name + "\":\"" + value + "\"";
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
