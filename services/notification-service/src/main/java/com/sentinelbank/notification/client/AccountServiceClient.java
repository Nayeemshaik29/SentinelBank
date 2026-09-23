package com.sentinelbank.notification.client;

import java.util.UUID;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The first step in resolving who to notify: {@code fromAccountId} -&gt; {@code ownerId}. Every exception
 * is left to propagate raw (see transaction-service's identically-shaped client for the full reasoning
 * about why resilience4j needs that) — the caller here is
 * {@link com.sentinelbank.notification.service.TransferNotificationService}, and an unresolved recipient
 * simply means the Kafka listener's exception propagates, the message retries, and the customer's account
 * or the whole account-service being briefly unavailable resolves itself by the time a later attempt runs.
 */
@Component
public class AccountServiceClient {

	private final RestClient restClient;

	AccountServiceClient(RestClient accountServiceRestClient) {
		this.restClient = accountServiceRestClient;
	}

	@CircuitBreaker(name = "account-service")
	@Retry(name = "account-service")
	public AccountView getAccount(UUID accountId) {
		return restClient.get().uri("/internal/accounts/{id}", accountId).retrieve().body(AccountView.class);
	}
}
