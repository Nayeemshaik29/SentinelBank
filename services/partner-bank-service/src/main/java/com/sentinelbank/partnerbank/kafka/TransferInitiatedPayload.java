package com.sentinelbank.partnerbank.kafka;

import java.util.UUID;

/**
 * This service's own view of the {@code transfer.initiated} payload shape, matched by field name during
 * deserialization — deliberately not shared with transaction-service's own copy of this record. Each
 * consumer owns the shape it expects; that is what keeps the two services independently deployable.
 */
public record TransferInitiatedPayload(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
		String currency) {
}
