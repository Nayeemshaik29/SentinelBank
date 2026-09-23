package com.sentinelbank.notification.client;

import java.util.UUID;

/** This service's own view of auth-service's user response, matched by field name. Only {@code email} (and
 * {@code fullName}, for a friendlier greeting) are actually needed here. */
public record UserView(UUID id, String email, String fullName) {
}
