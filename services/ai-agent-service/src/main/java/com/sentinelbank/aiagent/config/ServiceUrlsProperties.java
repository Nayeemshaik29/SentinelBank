package com.sentinelbank.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URLs of the two services this one reads from directly to answer a question: the caller's own
 * accounts and their recent transfers. */
@ConfigurationProperties("sentinelbank.services")
public record ServiceUrlsProperties(String account, String transaction) {
}
