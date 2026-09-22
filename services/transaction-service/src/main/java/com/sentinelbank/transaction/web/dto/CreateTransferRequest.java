package com.sentinelbank.transaction.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTransferRequest(
		@NotNull UUID fromAccountId,
		@NotBlank @Size(max = 64) String toAccountId,
		@Positive long amountMinor) {
}
