package com.sentinelbank.account.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** {@code referenceId} is the idempotency key: the same value can be safely retried any number of times. */
public record DebitCreditRequest(
		@NotBlank @Size(max = 64) String referenceId,
		@Positive long amountMinor,
		@Size(max = 200) String description) {
}
