package com.sentinelbank.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Public path to service mapping. The "/api" prefix is stripped before forwarding, so
 * {@code /api/auth/login} reaches the auth service as {@code /auth/login}.
 */
@Configuration
class RoutesConfig {

	@Bean
	RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties properties) {
		GatewayProperties.Services urls = properties.services();
		return builder.routes()
				.route("auth", r -> r.path("/api/auth/**").filters(f -> f.stripPrefix(1)).uri(urls.auth()))
				.route("accounts", r -> r.path("/api/accounts/**").filters(f -> f.stripPrefix(1)).uri(urls.account()))
				.route("transfers",
						r -> r.path("/api/transfers/**").filters(f -> f.stripPrefix(1)).uri(urls.transaction()))
				.route("fraud", r -> r.path("/api/fraud/**").filters(f -> f.stripPrefix(1)).uri(urls.fraud()))
				.route("audit", r -> r.path("/api/audit/**").filters(f -> f.stripPrefix(1)).uri(urls.audit()))
				.route("ai", r -> r.path("/api/ai/**").filters(f -> f.stripPrefix(1)).uri(urls.aiAgent()))
				.build();
	}
}
