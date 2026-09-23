package com.sentinelbank.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Two tiers of retry, matching the two-topic retry/DLT design: a few fast, in-place retries on the main
 * topic, then — if those are exhausted — the message moves to the {@code .retry} topic for a few slower
 * retries, and only after that does it reach the {@code .dlt} topic to wait for a human. Same shape as
 * transaction-service's, partner-bank-service's and fraud-service's copies of this class — now the fourth
 * (soon fifth, with notification-service), which is well past the "extract to common" point the earlier
 * copies' own comments flagged; left as-is here to keep this day's change scoped to Notification + Audit
 * rather than also touching three already-shipped services, but noted for a follow-up.
 */
@ConfigurationProperties("sentinelbank.kafka")
public record KafkaResilienceProperties(Attempts retry, Attempts retryTopic) {

	public record Attempts(int maxAttempts, long backoffMs) {
	}
}
