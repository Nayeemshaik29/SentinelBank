package com.sentinelbank.partnerbank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sentinelbank.partner-bank")
public record PartnerBankProperties(String failureTriggerPrefix) {
}
