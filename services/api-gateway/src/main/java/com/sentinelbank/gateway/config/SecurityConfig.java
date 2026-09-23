package com.sentinelbank.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.sentinelbank.gateway.web.ProblemResponses;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * The gateway is the single place where tokens are checked. Access rules live here, in one table:
 *
 * <ul>
 * <li>register, login, refresh, logout and health: public</li>
 * <li>/api/fraud/** and /api/audit/**: ANALYST only</li>
 * <li>/api/transfers/** and /api/ai/**: CUSTOMER only</li>
 * <li>everything else: any signed-in user</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewayProperties.class)
class SecurityConfig {

	@Bean
	SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder,
			CorsConfigurationSource corsConfigurationSource) {
		ServerAuthenticationEntryPoint unauthorized = (exchange, ex) -> ProblemResponses.write(exchange,
				HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required or the token is invalid");
		ServerAccessDeniedHandler forbidden = (exchange, ex) -> ProblemResponses.write(exchange,
				HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to use this resource");

		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers(HttpMethod.OPTIONS).permitAll()
						.pathMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
								"/api/auth/logout")
						.permitAll()
						.pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.pathMatchers("/api/fraud/**").hasRole("ANALYST")
						.pathMatchers("/api/audit/**").hasRole("ANALYST")
						.pathMatchers("/api/ai/**").hasRole("CUSTOMER")
						// Only a customer moves money, but an analyst can read any transfer for fraud
						// investigation (transaction-service already allows that at the application level) —
						// so only the write is customer-only; reads are open to either role.
						.pathMatchers(HttpMethod.POST, "/api/transfers/**").hasRole("CUSTOMER")
						.pathMatchers(HttpMethod.GET, "/api/transfers/**").hasAnyRole("CUSTOMER", "ANALYST")
						.anyExchange().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(unauthorized)
						.accessDeniedHandler(forbidden))
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(unauthorized)
						.accessDeniedHandler(forbidden)
						.jwt(jwt -> jwt
								.jwtDecoder(jwtDecoder)
								.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(
										rolesConverter()))))
				.build();
	}

	@Bean
	ReactiveJwtDecoder reactiveJwtDecoder(GatewayProperties properties) {
		byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("sentinelbank.jwt.secret must be at least 32 bytes long for HS256");
		}
		SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
		return decoder;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.cors().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
		config.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	/** Turns the "roles" claim into ROLE_CUSTOMER / ROLE_ANALYST authorities. */
	private static JwtAuthenticationConverter rolesConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}
}
