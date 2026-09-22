package com.sentinelbank.transaction.client;

import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import com.sentinelbank.common.web.IdentityHeaders;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The only way transaction-service talks to account-service: one read (checks the caller owns the account
 * and learns its currency) and one write (the debit itself). Both go through the same named circuit
 * breaker and retry policy ("account-service" in application.yaml), since they share one downstream
 * dependency.
 *
 * <p>Retrying the debit is safe specifically because it is idempotent by {@code referenceId} (Day 3): if
 * the debit actually succeeded on account-service but the response was lost, a retry returns the very same
 * ledger entry rather than debiting twice.
 *
 * <p>{@link #getAccount} and {@link #debit} deliberately let every exception propagate raw. The resilience4j
 * aspects wrap the call and inspect that raw exception to decide whether to retry or trip the breaker (see
 * {@code ignore-exceptions} in application.yaml) — if this method translated errors itself first, the
 * aspect would only ever see the translated type and that configuration would silently stop matching
 * anything. {@link #translateFailure} does the translating, but only after resilience4j is done, in
 * whichever exception finally reaches the caller.
 */
@Component
public class AccountServiceClient {

	private final RestClient restClient;

	AccountServiceClient(RestClient accountServiceRestClient) {
		this.restClient = accountServiceRestClient;
	}

	@CircuitBreaker(name = "account-service")
	@Retry(name = "account-service")
	public AccountView getAccount(UUID accountId, UUID callerId) {
		return restClient.get()
				.uri("/accounts/{id}", accountId)
				.header(IdentityHeaders.USER_ID, callerId.toString())
				.retrieve()
				.body(AccountView.class);
	}

	@CircuitBreaker(name = "account-service")
	@Retry(name = "account-service")
	public void debit(UUID accountId, String referenceId, long amountMinor, String description) {
		restClient.post()
				.uri("/internal/accounts/{id}/debit", accountId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new DebitCreditRequestBody(referenceId, amountMinor, description))
				.retrieve()
				.toBodilessEntity();
	}

	/**
	 * Turns whatever went wrong calling account-service (a business rejection, a timeout, retries
	 * exhausted, or the circuit sitting open) into the same kind of {@link ApiException} this service
	 * throws itself, so callers only ever need to catch one exception type.
	 */
	public ApiException translateFailure(RuntimeException ex) {
		if (ex instanceof CallNotPermittedException) {
			// The circuit is open: account-service has been failing enough that we stop calling it for a
			// while, so every caller gets a fast, clear answer instead of piling onto a struggling service.
			return serviceUnavailable();
		}
		if (ex instanceof ResourceAccessException) {
			// Connection refused, DNS failure, or the timeout set in RestClientConfig: account-service was
			// never reached at all, so there is no response body to translate.
			return serviceUnavailable();
		}
		if (ex instanceof HttpServerErrorException) {
			return serviceUnavailable();
		}
		if (ex instanceof RestClientResponseException responseException) {
			if (responseException.getStatusCode().is5xxServerError()) {
				return serviceUnavailable();
			}
			ProblemBody body = readProblemBody(responseException);
			if (body != null && body.code() != null) {
				return new ApiException(HttpStatus.valueOf(responseException.getStatusCode().value()), body.code(),
						body.detail() != null ? body.detail() : responseException.getMessage());
			}
		}
		return serviceUnavailable();
	}

	private ProblemBody readProblemBody(RestClientResponseException ex) {
		try {
			return ex.getResponseBodyAs(ProblemBody.class);
		}
		catch (RuntimeException parseFailure) {
			return null;
		}
	}

	private static ApiException serviceUnavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_SERVICE_UNAVAILABLE",
				"The account service is not available right now, please try again");
	}
}
