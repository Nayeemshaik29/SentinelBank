package com.sentinelbank.notification.kafka;

import java.util.UUID;

/** This service's own view of the {@code transfer.completed} / {@code transfer.failed} payload shape,
 * matched by field name — the same deliberate duplication as every other consumer of these events
 * (transaction-service, and originally partner-bank-service, which publishes them). */
public record TransferResultPayload(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
		String currency, String reason) {
}
