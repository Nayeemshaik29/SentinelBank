package com.sentinelbank.aiagent.client;

import java.util.UUID;

/** This service's own view of account-service's account response, matched by field name — the same
 * deliberate duplication every consumer of another service's response shape uses in this project. */
public record AccountView(UUID id, String accountNumber, String currency, long balanceMinor, String status) {
}
