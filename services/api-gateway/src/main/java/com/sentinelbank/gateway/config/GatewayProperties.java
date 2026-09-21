package com.sentinelbank.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All gateway settings, bound from the {@code sentinelbank.*} keys in application.yaml. */
@ConfigurationProperties("sentinelbank")
public record GatewayProperties(Jwt jwt, Services services, RateLimit rateLimit, Cors cors) {

	public record Jwt(String secret, String issuer) {
	}

	/** Base URLs of the downstream services. */
	public record Services(String auth, String account, String transaction, String fraud, String aiAgent) {
	}

	public record RateLimit(int generalPerMinute, int authPerMinute) {
	}

	public record Cors(List<String> allowedOrigins) {
	}
}
