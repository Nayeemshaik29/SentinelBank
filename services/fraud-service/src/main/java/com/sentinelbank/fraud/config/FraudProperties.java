package com.sentinelbank.fraud.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sentinelbank.fraud")
public record FraudProperties(long largeAmountMinor, long roundAmountModulus, Duration velocityWindow,
		long velocityThreshold) {
}
