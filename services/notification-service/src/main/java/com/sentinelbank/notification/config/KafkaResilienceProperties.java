package com.sentinelbank.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Two tiers of retry, matching the two-topic retry/DLT design: a few fast, in-place retries on the main
 * topic, then — if those are exhausted — the message moves to the {@code .retry} topic for a few slower
 * retries, and only after that does it reach the {@code .dlt} topic to wait for a human. Same shape as
 * transaction-service's, partner-bank-service's, fraud-service's and audit-service's copies of this class
 * — see audit-service's copy for why this is being left duplicated rather than extracted to {@code common}
 * in this same change.
 */
@ConfigurationProperties("sentinelbank.kafka")
public record KafkaResilienceProperties(Attempts retry, Attempts retryTopic) {

	public record Attempts(int maxAttempts, long backoffMs) {
	}
}
