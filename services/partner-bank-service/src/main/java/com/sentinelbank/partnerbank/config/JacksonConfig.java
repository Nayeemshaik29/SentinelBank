package com.sentinelbank.partnerbank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** See transaction-service's identical class: Boot 4's default ObjectMapper is Jackson 3, but the envelope
 * and payload classes here use the Jackson 2 API brought in transitively, so this service provides its own
 * Jackson 2 bean, with java.time support and readable ISO-8601 timestamps. */
@Configuration
class JacksonConfig {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper()
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}
}
