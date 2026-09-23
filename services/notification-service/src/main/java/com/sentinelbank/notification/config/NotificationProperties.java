package com.sentinelbank.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sentinelbank.notification")
public record NotificationProperties(String fromAddress) {
}
