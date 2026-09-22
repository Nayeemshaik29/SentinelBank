package com.sentinelbank.transaction.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sentinelbank.outbox")
public record OutboxProperties(long pollIntervalMs, Duration sendTimeout) {
}
