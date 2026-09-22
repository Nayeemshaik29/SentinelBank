package com.sentinelbank.transaction.config;

import java.util.function.UnaryOperator;

import com.sentinelbank.common.event.Topics;
import org.apache.kafka.common.TopicPartition;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Same two-tier retry/DLT wiring as partner-bank-service's identically-named class (see its javadoc for the
 * full rationale): a few fast retries on the main topic, then the {@code .retry} topic, then {@code .dlt}.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaResilienceProperties.class)
class KafkaConsumerConfig {

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> mainListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate,
			KafkaResilienceProperties properties) {
		return factory(consumerFactory, kafkaTemplate, properties.retry(), Topics::retry);
	}

	@Bean
	ConcurrentKafkaListenerContainerFactory<String, String> retryListenerContainerFactory(
			ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate,
			KafkaResilienceProperties properties) {
		// record.topic() here is already "...retry", so the destination must strip that suffix first —
		// see Topics.base's javadoc for why (this is exactly the bug it was added to prevent).
		return factory(consumerFactory, kafkaTemplate, properties.retryTopic(),
				topic -> Topics.dlt(Topics.base(topic)));
	}

	private ConcurrentKafkaListenerContainerFactory<String, String> factory(
			ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate,
			KafkaResilienceProperties.Attempts attempts, UnaryOperator<String> nextTopic) {
		ConcurrentKafkaListenerContainerFactory<String, String> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory);
		factory.setCommonErrorHandler(errorHandler(kafkaTemplate, attempts, nextTopic));
		return factory;
	}

	private DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate,
			KafkaResilienceProperties.Attempts attempts, UnaryOperator<String> nextTopic) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, ex) -> new TopicPartition(nextTopic.apply(record.topic()), -1));
		FixedBackOff backOff = new FixedBackOff(attempts.backoffMs(), Math.max(0, attempts.maxAttempts() - 1));
		return new DefaultErrorHandler(recoverer, backOff);
	}
}
