package com.sentinelbank.common.web;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;

/**
 * Reads the identity the gateway attaches to a request after verifying the caller's JWT (see
 * {@link IdentityHeaders}). Every servlet service trusts these headers completely: the gateway strips any
 * client-supplied copies before setting its own.
 */
public final class CallerIdentity {

	private CallerIdentity() {
	}

	public static UUID requireUserId(HttpServletRequest request) {
		return optionalUserId(request).orElseThrow(CallerIdentity::missingIdentity);
	}

	public static Optional<UUID> optionalUserId(HttpServletRequest request) {
		String header = request.getHeader(IdentityHeaders.USER_ID);
		if (header == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(header));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	public static boolean isAnalyst(HttpServletRequest request) {
		String roles = request.getHeader(IdentityHeaders.USER_ROLES);
		return roles != null && Arrays.stream(roles.split(",")).anyMatch("ANALYST"::equals);
	}

	private static ApiException missingIdentity() {
		// Reaching a service without the gateway's identity headers means it was called directly,
		// bypassing the gateway, which should never happen for a customer-facing endpoint.
		return new ApiException(HttpStatus.UNAUTHORIZED, "MISSING_IDENTITY", "Caller identity is missing");
	}
}
