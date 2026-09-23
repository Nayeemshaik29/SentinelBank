package com.sentinelbank.notification.client;

import java.util.UUID;

/** This service's own view of account-service's account response, matched by field name — the same
 * deliberate duplication every consumer in this project uses for a producer's payload shape. Only
 * {@code ownerId} is actually needed here. */
public record AccountView(UUID id, UUID ownerId) {
}
