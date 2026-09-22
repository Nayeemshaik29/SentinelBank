package com.sentinelbank.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Same shape as partner-bank-service's and transaction-service's copy of this class — see either for the
 * full rationale. A candidate to extract into {@code common} once a fourth service needs it too. */
@ConfigurationProperties("sentinelbank.kafka")
public record KafkaResilienceProperties(Attempts retry, Attempts retryTopic) {

	public record Attempts(int maxAttempts, long backoffMs) {
	}
}
