package com.sentinelbank.transaction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's own auto-configuration wires up Jackson 3 ({@code tools.jackson.databind.ObjectMapper}),
 * not the classic Jackson 2 ({@code com.fasterxml.jackson.databind.ObjectMapper}) that a transitive
 * dependency (the resilience4j starter) still brings onto the classpath. TransferWriteOperations and
 * OutboxPublisher need the Jackson 2 API to serialize outbox payloads and event envelopes (which carry a
 * java.time.Instant), so this service provides that bean itself, with JavaTimeModule registered.
 */
@Configuration
class JacksonConfig {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper()
				.registerModule(new JavaTimeModule())
				// ISO-8601 strings ("2026-01-01T00:00:00Z"), not a raw epoch-seconds number: readable in
				// logs, and unambiguous for the Kafka consumers (Day 6 onward) that will parse occurredAt.
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}
}
