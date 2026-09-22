package com.sentinelbank.partnerbank.domain;

import java.util.UUID;

/** The body of a {@code transfer.completed} or {@code transfer.failed} outbox event. */
public record TransferResultPayload(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
		String currency, String reason) {
}
