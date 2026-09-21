package com.sentinelbank.gateway.filter;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gives every request a correlation ID (or keeps the one the caller sent), forwards it to the services,
 * echoes it on the response and writes one access-log line. It runs before Spring Security so even
 * rejected requests (401, 403) carry an ID.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

	public static final String HEADER = "X-Correlation-Id";

	private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
		String id = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

		ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, id).build();
		exchange.getResponse().getHeaders().set(HEADER, id);
		long started = System.nanoTime();

		return chain.filter(exchange.mutate().request(request).build())
				.doFinally(signal -> log.info("{} {} -> {} in {} ms [correlationId={}]", request.getMethod(),
						request.getURI().getPath(), exchange.getResponse().getStatusCode(),
						(System.nanoTime() - started) / 1_000_000, id));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}
}
