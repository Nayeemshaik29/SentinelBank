package com.sentinelbank.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.sentinelbank.common.error.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, single-use refresh tokens.
 *
 * <ul>
 * <li>The raw token is random and shown to the client once; only its SHA-256 hash is stored.</li>
 * <li>Every use rotates it: the old token is revoked and a new one is issued.</li>
 * <li>Presenting an already-revoked token means it was probably stolen, so every session of that user
 * is revoked.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

	private static final String INVALID = "INVALID_REFRESH_TOKEN";

	private final SecureRandom random = new SecureRandom();

	private final RefreshTokenRepository repository;

	private final JwtProperties properties;

	RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	/** Creates a new refresh token for the user and returns the raw value to hand to the client. */
	@Transactional
	public String issue(UUID userId) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		repository.save(new RefreshToken(userId, sha256(raw), Instant.now().plus(properties.refreshTokenTtl())));
		return raw;
	}

	/**
	 * Consumes a refresh token and returns the user it belonged to. The caller then issues a fresh pair.
	 * {@code noRollbackFor} matters: when reuse is detected we revoke everything and then throw, and that
	 * revocation must still be committed.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public UUID consume(String raw) {
		Instant now = Instant.now();
		RefreshToken token = repository.findByTokenHash(sha256(raw))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, INVALID, "Invalid refresh token"));
		if (token.isRevoked()) {
			repository.revokeAllForUser(token.getUserId(), now);
			throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID, "Invalid refresh token");
		}
		if (token.isExpired(now)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID, "Refresh token expired");
		}
		token.revoke(now);
		return token.getUserId();
	}

	/** Logout: revoke one token. Unknown or already revoked tokens are ignored (logout is idempotent). */
	@Transactional
	public void revoke(String raw) {
		repository.findByTokenHash(sha256(raw)).ifPresent(token -> token.revoke(Instant.now()));
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
