package com.sentinelbank.common.web;

/**
 * Names of the headers the gateway sets after verifying the caller's JWT, so downstream services can read
 * who is calling without parsing a token themselves. The gateway strips these headers from every incoming
 * request first, so a service can trust them completely.
 */
public final class IdentityHeaders {

	public static final String USER_ID = "X-User-Id";

	public static final String USER_EMAIL = "X-User-Email";

	public static final String USER_ROLES = "X-User-Roles";

	private IdentityHeaders() {
	}
}
