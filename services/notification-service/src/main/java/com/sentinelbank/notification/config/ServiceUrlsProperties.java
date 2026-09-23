package com.sentinelbank.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URLs of the two services this one calls directly (never through the gateway) to resolve a
 * transfer's recipient: {@code fromAccountId} -&gt; {@code ownerId} -&gt; email address. */
@ConfigurationProperties("sentinelbank.services")
public record ServiceUrlsProperties(String account, String auth) {
}
