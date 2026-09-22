package com.sentinelbank.transaction.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.sentinelbank.transaction.domain.Transfer;
import com.sentinelbank.transaction.domain.TransferStatus;

public record TransferResponse(UUID id, UUID ownerId, UUID fromAccountId, String toAccountId, String currency,
		long amountMinor, TransferStatus status, String failureCode, String failureDetail, Instant createdAt,
		Instant updatedAt) {

	public static TransferResponse from(Transfer transfer) {
		return new TransferResponse(transfer.getId(), transfer.getOwnerId(), transfer.getFromAccountId(),
				transfer.getToAccountId(), transfer.getCurrency(), transfer.getAmountMinor(),
				transfer.getStatus(), transfer.getFailureCode(), transfer.getFailureDetail(),
				transfer.getCreatedAt(), transfer.getUpdatedAt());
	}
}
