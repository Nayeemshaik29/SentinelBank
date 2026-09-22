package com.sentinelbank.transaction.kafka;

import java.util.UUID;

/**
 * This service's own view of the {@code transfer.completed} / {@code transfer.failed} payload shape,
 * matched by field name — deliberately not shared with partner-bank-service's own copy of this record. See
 * {@code TransferInitiatedPayload} in partner-bank-service for the same reasoning.
 */
public record TransferResultPayload(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
		String currency, String reason) {
}
