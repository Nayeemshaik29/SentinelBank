package com.sentinelbank.transaction.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AccountServiceProperties.class)
class RestClientConfig {

	@Bean
	RestClient accountServiceRestClient(AccountServiceProperties properties) {
		// A service that is merely slow must not be allowed to hang a caller forever: a short, fixed
		// timeout turns "no answer" into a ResourceAccessException, which the resilience config below
		// treats as retryable and, if it keeps happening, trips the circuit breaker.
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(2_000);
		requestFactory.setReadTimeout(5_000);
		return RestClient.builder().baseUrl(properties.accountUrl()).requestFactory(requestFactory).build();
	}
}
