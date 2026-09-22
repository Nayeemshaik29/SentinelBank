package com.sentinelbank.account.web;

import java.util.Arrays;
import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import com.sentinelbank.common.web.IdentityHeaders;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;

/**
 * Reads the identity the gateway attached to the request. This service has no Spring Security of its own:
 * it trusts these headers completely, because the gateway strips any client-supplied copies of them before
 * setting its own (see {@code IdentityHeadersGlobalFilter}).
 */
final class CallerContext {

	private CallerContext() {
	}

	static UUID userId(HttpServletRequest request) {
		String header = request.getHeader(IdentityHeaders.USER_ID);
		if (header == null) {
			throw missingIdentity();
		}
		try {
			return UUID.fromString(header);
		}
		catch (IllegalArgumentException ex) {
			throw missingIdentity();
		}
	}

	static boolean isAnalyst(HttpServletRequest request) {
		String roles = request.getHeader(IdentityHeaders.USER_ROLES);
		return roles != null && Arrays.stream(roles.split(",")).anyMatch("ANALYST"::equals);
	}

	private static ApiException missingIdentity() {
		// Reaching this service without the gateway's identity headers means it was called directly,
		// bypassing the gateway, which should never happen for these customer-facing endpoints.
		return new ApiException(HttpStatus.UNAUTHORIZED, "MISSING_IDENTITY", "Caller identity is missing");
	}
}
