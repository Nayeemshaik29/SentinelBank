package com.sentinelbank.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** A throwaway real PostgreSQL for the tests. Kafka is started and wired separately (see
 * TransferResultListenerTests): @ServiceConnection on Kafka did not reliably win over the
 * spring.kafka.bootstrap-servers property already defined in application.yaml, the same issue found and
 * fixed in every other Kafka-consuming service in this project. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}
}
