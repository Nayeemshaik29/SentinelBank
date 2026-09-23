package com.sentinelbank.audit;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** A throwaway real MongoDB for the tests. Kafka is started and wired separately (see
 * AuditEventListenerTests): @ServiceConnection on Kafka did not reliably win over the
 * spring.kafka.bootstrap-servers property already defined in application.yaml, the same issue found and
 * fixed in every other Kafka-consuming service in this project. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		return new MongoDBContainer(DockerImageName.parse("mongo:8"));
	}
}
