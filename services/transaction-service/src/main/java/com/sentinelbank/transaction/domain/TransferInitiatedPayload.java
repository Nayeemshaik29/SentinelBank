package com.sentinelbank.transaction.domain;

import java.util.UUID;

/** The body of a {@code transfer.initiated} outbox event, serialized to JSON by {@link TransferWriteOperations}. */
public record TransferInitiatedPayload(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
		String currency) {
}
