package com.sentinelbank.transaction.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sentinelbank.services")
public record AccountServiceProperties(String accountUrl) {
}
