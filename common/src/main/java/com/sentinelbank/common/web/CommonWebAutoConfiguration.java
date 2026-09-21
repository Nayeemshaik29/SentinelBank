package com.sentinelbank.common.web;

import com.sentinelbank.common.error.GlobalExceptionHandler;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Wires the shared web behaviour into any servlet-based service that depends on {@code common}:
 * the correlation-ID filter and the problem-detail error handler. The reactive gateway is unaffected.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
public class CommonWebAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	CorrelationIdFilter correlationIdFilter() {
		return new CorrelationIdFilter();
	}

	@Bean
	@ConditionalOnMissingBean
	GlobalExceptionHandler globalExceptionHandler() {
		return new GlobalExceptionHandler();
	}
}
