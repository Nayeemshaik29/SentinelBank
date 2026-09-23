package com.sentinelbank.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sentinelbank.ai")
public record AiAgentProperties(int maxRecentTransfers) {
}
