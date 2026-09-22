package com.sentinelbank.partnerbank.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.partnerbank.domain.OutboxEvent;
import com.sentinelbank.partnerbank.domain.OutboxEventRepository;
import com.sentinelbank.partnerbank.domain.OutboxWriteOperations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The same outbox publisher pattern as transaction-service: a scheduled poller, decoupled from the request
 * (here, the Kafka message) that created the row, so Kafka being briefly unreachable never loses a
 * {@code transfer.completed}/{@code transfer.failed} result — it just waits in the table.
 */
@Component
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

	private final OutboxEventRepository outboxEvents;

	private final OutboxWriteOperations writeOperations;

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final ObjectMapper objectMapper;

	private final OutboxProperties properties;

	OutboxPublisher(OutboxEventRepository outboxEvents, OutboxWriteOperations writeOperations,
			KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, OutboxProperties properties) {
		this.outboxEvents = outboxEvents;
		this.writeOperations = writeOperations;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${sentinelbank.outbox.poll-interval-ms}")
	public void publishPending() {
		List<OutboxEvent> pending = outboxEvents.findByPublishedAtIsNullOrderByCreatedAtAsc();
		for (OutboxEvent event : pending) {
			publishOne(event);
		}
	}

	private void publishOne(OutboxEvent event) {
		try {
			String envelopeJson = buildEnvelopeJson(event);
			kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), envelopeJson)
					.get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
			writeOperations.markPublished(event.getId());
		}
		catch (Exception ex) {
			log.warn("Failed to publish outbox event {} ({}); it will be retried on the next poll",
					event.getId(), event.getEventType(), ex);
		}
	}

	private String buildEnvelopeJson(OutboxEvent event) throws Exception {
		JsonNode payload = objectMapper.readTree(event.getPayload());
		EventEnvelope<JsonNode> envelope = new EventEnvelope<>(event.getId(), event.getEventType(),
				event.getAggregateId().toString(), event.getCreatedAt(), event.getCorrelationId(), payload);
		return objectMapper.writeValueAsString(envelope);
	}
}
