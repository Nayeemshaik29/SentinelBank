package com.sentinelbank.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signs access tokens the same way the auth service does, so tests can act as any user. */
final class TestTokens {

	static final String SECRET = "test-secret-test-secret-test-secret-1234";

	private TestTokens() {
	}

	static String token(String userId, String email, List<String> roles) {
		return token(userId, email, roles, Duration.ofMinutes(15), SECRET);
	}

	static String token(String userId, String email, List<String> roles, Duration validFor, String secret) {
		Instant expiresAt = Instant.now().plus(validFor);
		Instant issuedAt = expiresAt.minus(Duration.ofMinutes(15));
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("sentinelbank")
				.subject(userId)
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.id(UUID.randomUUID().toString())
				.claim("email", email)
				.claim("roles", roles)
				.build();
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(
				new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}
}
