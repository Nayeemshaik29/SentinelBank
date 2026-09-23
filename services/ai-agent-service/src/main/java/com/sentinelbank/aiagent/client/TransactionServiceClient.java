package com.sentinelbank.aiagent.client;

import java.util.List;
import java.util.UUID;

import com.sentinelbank.common.web.IdentityHeaders;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** The assistant's read-only "getRecentTransactions" tool: {@code GET /transfers}, forwarding the
 * caller's own identity — see {@link AccountServiceClient} for the full reasoning. */
@Component
public class TransactionServiceClient {

	private final RestClient restClient;

	TransactionServiceClient(RestClient transactionServiceRestClient) {
		this.restClient = transactionServiceRestClient;
	}

	public List<TransferView> listMine(UUID userId) {
		return restClient.get()
				.uri("/transfers")
				.header(IdentityHeaders.USER_ID, userId.toString())
				.retrieve()
				.body(new ParameterizedTypeReference<List<TransferView>>() {
				});
	}
}
