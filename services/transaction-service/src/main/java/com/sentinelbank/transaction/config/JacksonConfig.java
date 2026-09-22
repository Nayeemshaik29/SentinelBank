package com.sentinelbank.transaction.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's own auto-configuration wires up Jackson 3 ({@code tools.jackson.databind.ObjectMapper}),
 * not the classic Jackson 2 ({@code com.fasterxml.jackson.databind.ObjectMapper}) that a transitive
 * dependency (the resilience4j starter) still brings onto the classpath. TransferWriteOperations needs the
 * Jackson 2 API to serialize outbox payloads, so this service provides that bean itself.
 */
@Configuration
class JacksonConfig {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
