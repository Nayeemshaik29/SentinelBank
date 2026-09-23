package com.sentinelbank.aiagent.client;

import java.util.List;
import java.util.UUID;

import com.sentinelbank.common.web.IdentityHeaders;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The assistant's read-only "getBalance" tool (see the README's Day 11 section for why this is a plain
 * Java call rather than an LLM-invoked function tool): {@code GET /accounts}, forwarding the caller's own
 * identity — the exact same {@code X-User-Id} trust pattern transaction-service already uses to call
 * account-service — so this only ever sees the signed-in customer's own accounts, never anyone else's.
 */
@Component
public class AccountServiceClient {

	private final RestClient restClient;

	AccountServiceClient(RestClient accountServiceRestClient) {
		this.restClient = accountServiceRestClient;
	}

	public List<AccountView> listMine(UUID userId) {
		return restClient.get()
				.uri("/accounts")
				.header(IdentityHeaders.USER_ID, userId.toString())
				.retrieve()
				.body(new ParameterizedTypeReference<List<AccountView>>() {
				});
	}
}
