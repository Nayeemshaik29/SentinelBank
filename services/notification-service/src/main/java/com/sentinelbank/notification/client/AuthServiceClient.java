package com.sentinelbank.notification.client;

import java.util.UUID;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** The second step in resolving who to notify: {@code ownerId} -&gt; email address. See
 * {@link AccountServiceClient} for the reasoning behind letting every exception propagate raw here too. */
@Component
public class AuthServiceClient {

	private final RestClient restClient;

	AuthServiceClient(RestClient authServiceRestClient) {
		this.restClient = authServiceRestClient;
	}

	@CircuitBreaker(name = "auth-service")
	@Retry(name = "auth-service")
	public UserView getUser(UUID userId) {
		return restClient.get().uri("/internal/users/{id}", userId).retrieve().body(UserView.class);
	}
}
