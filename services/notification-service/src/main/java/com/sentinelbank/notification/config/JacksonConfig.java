package com.sentinelbank.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** See transaction-service's identical class for the full rationale (Boot 4 defaults to Jackson 3; this
 * service builds its own Jackson 2 ObjectMapper for envelopes carrying java.time.Instant). */
@Configuration
class JacksonConfig {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper()
				.registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}
}
