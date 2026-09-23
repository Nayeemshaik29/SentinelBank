package com.sentinelbank.aiagent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ServiceUrlsProperties.class)
class RestClientConfig {

	@Bean
	RestClient accountServiceRestClient(ServiceUrlsProperties properties) {
		return RestClient.builder().baseUrl(properties.account()).requestFactory(shortTimeout()).build();
	}

	@Bean
	RestClient transactionServiceRestClient(ServiceUrlsProperties properties) {
		return RestClient.builder().baseUrl(properties.transaction()).requestFactory(shortTimeout()).build();
	}

	/**
	 * Spring AI's {@code OllamaApiAutoConfiguration} builds its own internal {@link RestClient} for talking
	 * to Ollama, but only from a {@code RestClient.Builder} it looks up from the context if one exists —
	 * otherwise it falls back to a completely un-timed-out default. Found the hard way: without this bean,
	 * a genuinely unreachable Ollama (the exact scenario this service is meant to degrade gracefully from —
	 * see PLAN.md's own "make the endpoint work with a stub when Ollama isn't running") does not fail
	 * fast; the underlying HTTP call simply hangs, past any sane request timeout, with no exception for
	 * {@link com.sentinelbank.aiagent.service.SpendingAssistantService} to ever catch. Ollama's own
	 * responses can legitimately take longer than a downstream microservice's (a local model generating a
	 * few hundred tokens is not instant), hence the longer read timeout than the two clients above.
	 */
	@Bean
	RestClient.Builder ollamaRestClientBuilder() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(3_000);
		requestFactory.setReadTimeout(30_000);
		return RestClient.builder().requestFactory(requestFactory);
	}

	/**
	 * The HTTP-level timeout above bounds one attempt, but {@code OllamaChatModel} wraps every call in its
	 * own {@code RetryTemplate} too (Spring AI's {@code RetryUtils.DEFAULT_RETRY_TEMPLATE}) — and its
	 * default policy kept retrying for minutes against a genuinely dead Ollama, which is far too long for
	 * this to "degrade gracefully" as intended. {@code OllamaChatAutoConfiguration} looks up a
	 * {@code RetryTemplate} bean the same way it looks up the {@code RestClient.Builder} above, so this
	 * replaces it with one attempt, a short delay, and a firm overall timeout — a spending assistant
	 * failing fast with a clear "try again shortly" beats a chat request the customer just watches spin.
	 */
	@Bean
	RetryTemplate ollamaRetryTemplate() {
		RetryPolicy retryPolicy = RetryPolicy.builder()
				.maxRetries(1)
				.delay(Duration.ofMillis(500))
				.timeout(Duration.ofSeconds(10))
				.build();
		return new RetryTemplate(retryPolicy);
	}

	/** A struggling downstream must not hang a chat request forever: a short, fixed timeout, same as
	 * notification-service's identically-shaped config, turns "no answer" into an exception the
	 * assistant catches and degrades gracefully from (see SpendingAssistantService), rather than the
	 * whole request hanging until the client gives up. */
	private SimpleClientHttpRequestFactory shortTimeout() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(2_000);
		requestFactory.setReadTimeout(5_000);
		return requestFactory;
	}
}
