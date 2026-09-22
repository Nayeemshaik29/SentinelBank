package com.sentinelbank.transaction.client;

import java.util.UUID;

/** Mirrors the fields of account-service's {@code AccountResponse} that this client actually needs. */
public record AccountView(UUID id, UUID ownerId, String accountNumber, String currency, long balanceMinor,
		String status) {
}
