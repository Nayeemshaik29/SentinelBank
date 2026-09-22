package com.sentinelbank.fraud.kafka;

import java.util.UUID;

/** This service's own view of the {@code transfer.initiated} payload shape, matched by field name — the
 * same deliberate duplication as partner-bank-service's copy of this record; see there for the reasoning. */
public record TransferInitiatedPayload(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
		String currency) {
}
