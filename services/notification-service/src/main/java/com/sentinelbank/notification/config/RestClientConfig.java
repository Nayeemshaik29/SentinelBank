package com.sentinelbank.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
	RestClient authServiceRestClient(ServiceUrlsProperties properties) {
		return RestClient.builder().baseUrl(properties.auth()).requestFactory(shortTimeout()).build();
	}

	/** A service that is merely slow must not be allowed to hang a Kafka consumer thread forever: a short,
	 * fixed timeout turns "no answer" into a retryable exception (see resilience4j config), same as
	 * transaction-service's identical account-service client. */
	private SimpleClientHttpRequestFactory shortTimeout() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(2_000);
		requestFactory.setReadTimeout(5_000);
		return requestFactory;
	}
}
