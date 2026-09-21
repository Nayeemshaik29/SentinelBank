package com.sentinelbank.gateway.filter;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.sentinelbank.gateway.config.GatewayProperties;
import com.sentinelbank.gateway.web.ProblemResponses;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Token-bucket rate limiting, kept in memory (fine for a single gateway instance; a multi-instance
 * deployment would move the buckets to Redis).
 *
 * <ul>
 * <li>Login, register and refresh: a strict limit per client IP, to slow down password guessing.</li>
 * <li>Everything else: a general limit per signed-in user, or per IP when anonymous.</li>
 * </ul>
 *
 * <p>The client IP is the direct peer address. Behind a load balancer, configure it to pass the real
 * address instead, or every user will share one bucket.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

	private static final Set<String> STRICT_PATHS = Set.of("/api/auth/login", "/api/auth/register",
			"/api/auth/refresh");

	private static final long IDLE_EVICTION_NANOS = Duration.ofMinutes(10).toNanos();

	private static final int SWEEP_EVERY = 1000;

	private final ConcurrentHashMap<String, Tracked> buckets = new ConcurrentHashMap<>();

	private final AtomicLong calls = new AtomicLong();

	private final GatewayProperties.RateLimit limits;

	RateLimitGlobalFilter(GatewayProperties properties) {
		this.limits = properties.rateLimit();
	}

	private static final class Tracked {

		final Bucket bucket;

		volatile long lastUsedNanos = System.nanoTime();

		Tracked(Bucket bucket) {
			this.bucket = bucket;
		}
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();
		boolean strict = STRICT_PATHS.contains(path);
		String userId = exchange.getRequest().getHeaders().getFirst(IdentityHeadersGlobalFilter.USER_ID);

		String key;
		int perMinute;
		if (strict) {
			key = "auth:" + clientAddress(exchange);
			perMinute = limits.authPerMinute();
		}
		else if (userId != null) {
			key = "user:" + userId;
			perMinute = limits.generalPerMinute();
		}
		else {
			key = "ip:" + clientAddress(exchange);
			perMinute = limits.generalPerMinute();
		}

		ConsumptionProbe probe = bucketFor(key, perMinute).bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
			exchange.getResponse().getHeaders().set("Retry-After", Long.toString(retryAfterSeconds));
			return ProblemResponses.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
					"Too many requests, try again in " + retryAfterSeconds + " seconds");
		}
		exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
		return chain.filter(exchange);
	}

	private Tracked bucketFor(String key, int perMinute) {
		if (calls.incrementAndGet() % SWEEP_EVERY == 0) {
			long now = System.nanoTime();
			buckets.values().removeIf(tracked -> now - tracked.lastUsedNanos > IDLE_EVICTION_NANOS);
		}
		Tracked tracked = buckets.computeIfAbsent(key, k -> new Tracked(Bucket.builder()
				.addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1))
						.build())
				.build()));
		tracked.lastUsedNanos = System.nanoTime();
		return tracked;
	}

	private static String clientAddress(ServerWebExchange exchange) {
		var address = exchange.getRequest().getRemoteAddress();
		return (address == null || address.getAddress() == null) ? "unknown"
				: address.getAddress().getHostAddress();
	}

	@Override
	public int getOrder() {
		return 0;
	}
}
