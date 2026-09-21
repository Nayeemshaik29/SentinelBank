package com.sentinelbank.gateway.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Writes RFC 9457 problem responses, matching the shape the servlet services produce through {@code common}. */
public final class ProblemResponses {

	private ProblemResponses() {
	}

	public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String detail) {
		ServerHttpResponse response = exchange.getResponse();
		if (response.isCommitted()) {
			return Mono.empty();
		}
		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		String body = "{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
				+ status.value() + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}";
		DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
		return response.writeWith(Mono.just(buffer));
	}
}
