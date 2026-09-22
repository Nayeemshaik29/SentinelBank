package com.sentinelbank.transaction;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** A throwaway real PostgreSQL for the tests, so Flyway migrations and JPA mappings run for real. Tests that
 * also need Kafka (see OutboxPublisherTests) start their own container and wire it with a
 * {@code @DynamicPropertySource}, since {@code @ServiceConnection} on Kafka did not reliably win over the
 * property already defined in application.yaml in this project's setup. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}
}
