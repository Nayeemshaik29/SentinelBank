package com.sentinelbank.aiagent.client;

import java.time.Instant;
import java.util.UUID;

/** This service's own view of transaction-service's transfer response, matched by field name. */
public record TransferView(UUID id, UUID fromAccountId, String toAccountId, String currency, long amountMinor,
		String status, Instant createdAt) {
}
