package com.sentinelbank.partnerbank.config;

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
 * Two listener container factories, one per retry tier (see {@link KafkaResilienceProperties}):
 *
 * <ul>
 * <li>{@code mainListenerContainerFactory}, for the business topics ({@code transfer.initiated}): a few
 * fast retries, then publish to that topic's {@code .retry} companion.</li>
 * <li>{@code retryListenerContainerFactory}, for the {@code .retry} topics: a few slower retries, then
 * publish to the {@code .dlt} companion, where it waits for a human to inspect and replay.</li>
 * </ul>
 *
 * <p>Both use {@code -1} as the destination partition, which tells {@link DeadLetterPublishingRecoverer} to
 * let the producer's own partitioner place the message by key — safe here because every topic in this
 * project has the same partition count, but robust even if that ever changed.
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
		// record.topic() here is already "...retry" (that is what this factory's listeners consume), so
		// the destination must strip that suffix first — otherwise this computes "...retry.dlt" instead
		// of "....dlt". See Topics.base's own javadoc for why it exists.
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
		// BackOff counts RETRIES after the first attempt, so maxAttempts=3 means 1 try + 2 retries.
		FixedBackOff backOff = new FixedBackOff(attempts.backoffMs(), Math.max(0, attempts.maxAttempts() - 1));
		return new DefaultErrorHandler(recoverer, backOff);
	}
}
