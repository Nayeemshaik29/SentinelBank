package com.sentinelbank.gateway.web;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import io.netty.handler.timeout.ReadTimeoutException;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Says the right thing when a service behind the gateway is down or too slow: 503 when it cannot be reached,
 * 504 when it takes too long, instead of a generic 500. Anything else is left to Spring's default handling.
 */
@Component
@Order(-2)
class DownstreamFailureHandler implements WebExceptionHandler {

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof ReadTimeoutException || cause instanceof SocketTimeoutException
					|| cause instanceof TimeoutException) {
				return ProblemResponses.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "DOWNSTREAM_TIMEOUT",
						"The service took too long to answer");
			}
			if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
				return ProblemResponses.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
						"The service is not available right now");
			}
			if (cause instanceof ResponseStatusException statusException
					&& (statusException.getStatusCode().isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)
							|| statusException.getStatusCode().isSameCodeAs(HttpStatus.GATEWAY_TIMEOUT))) {
				HttpStatus status = HttpStatus.valueOf(statusException.getStatusCode().value());
				return ProblemResponses.write(exchange, status,
						status == HttpStatus.GATEWAY_TIMEOUT ? "DOWNSTREAM_TIMEOUT" : "SERVICE_UNAVAILABLE",
						"The service is not available right now");
			}
		}
		return Mono.error(error);
	}
}
