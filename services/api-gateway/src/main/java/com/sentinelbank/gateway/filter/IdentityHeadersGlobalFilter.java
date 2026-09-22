package com.sentinelbank.gateway.filter;

import java.util.Optional;
import java.util.stream.Collectors;

import com.sentinelbank.common.web.IdentityHeaders;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Tells downstream services who is calling. After the gateway has verified the token it forwards the user
 * as plain headers (X-User-Id, X-User-Email, X-User-Roles), so services do not need to parse JWTs.
 *
 * <p>Security-critical: any such headers sent by the client are removed first. Otherwise anyone could
 * claim to be another user or an analyst simply by adding a header.
 */
@Component
public class IdentityHeadersGlobalFilter implements GlobalFilter, Ordered {

	// Re-exported from common so RateLimitGlobalFilter (and every downstream service) uses the exact
	// same header names, with a single place to change them.
	public static final String USER_ID = IdentityHeaders.USER_ID;

	public static final String USER_EMAIL = IdentityHeaders.USER_EMAIL;

	public static final String USER_ROLES = IdentityHeaders.USER_ROLES;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return ReactiveSecurityContextHolder.getContext()
				.map(context -> Optional.ofNullable(context.getAuthentication()))
				.defaultIfEmpty(Optional.empty())
				.flatMap(authentication -> {
					ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
						headers.remove(USER_ID);
						headers.remove(USER_EMAIL);
						headers.remove(USER_ROLES);
						authentication.filter(JwtAuthenticationToken.class::isInstance)
								.map(JwtAuthenticationToken.class::cast).ifPresent(token -> {
							Jwt jwt = token.getToken();
							headers.set(USER_ID, jwt.getSubject());
							String email = jwt.getClaimAsString("email");
							if (email != null) {
								headers.set(USER_EMAIL, email);
							}
							headers.set(USER_ROLES, token.getAuthorities().stream()
									.map(GrantedAuthority::getAuthority)
									// only real roles: Spring Security also adds internal ones such as FACTOR_BEARER
									.filter(authority -> authority.startsWith("ROLE_"))
									.map(authority -> authority.substring("ROLE_".length()))
									.collect(Collectors.joining(",")));
						});
					}).build();
					return chain.filter(exchange.mutate().request(request).build());
				});
	}

	@Override
	public int getOrder() {
		return -1;
	}
}
