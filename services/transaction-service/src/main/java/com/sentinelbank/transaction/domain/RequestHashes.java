package com.sentinelbank.transaction.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A fingerprint of the parts of a transfer request that must match on every replay of the same idempotency
 * key. Not a secret, just a fast way to tell "the same request, retried" from "a different request that
 * happens to reuse a key by mistake or by attack" (see {@code IDEMPOTENCY_KEY_REUSED}).
 */
public final class RequestHashes {

	private RequestHashes() {
	}

	public static String of(UUID fromAccountId, String toAccountId, long amountMinor) {
		String canonical = fromAccountId + "|" + toAccountId + "|" + amountMinor;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
