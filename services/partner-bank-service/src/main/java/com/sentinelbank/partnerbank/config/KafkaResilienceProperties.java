package com.sentinelbank.partnerbank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Two tiers of retry, matching the two-topic retry/DLT design: a few fast, in-place retries on the main
 * topic, then — if those are exhausted — the message moves to the {@code .retry} topic for a few slower
 * retries, and only after that does it reach the {@code .dlt} topic to wait for a human.
 */
@ConfigurationProperties("sentinelbank.kafka")
public record KafkaResilienceProperties(Attempts retry, Attempts retryTopic) {

	public record Attempts(int maxAttempts, long backoffMs) {
	}
}
