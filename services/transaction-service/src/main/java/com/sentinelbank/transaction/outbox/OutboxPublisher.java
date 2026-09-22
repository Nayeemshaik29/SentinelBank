package com.sentinelbank.transaction.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.transaction.domain.OutboxEvent;
import com.sentinelbank.transaction.domain.OutboxEventRepository;
import com.sentinelbank.transaction.domain.OutboxWriteOperations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The other half of the transactional outbox: everything up to here (writing the row, in the same
 * transaction as the state change it describes) is done atomically and locally. This poller is what turns
 * that durable row into an actual Kafka message, on its own schedule, independent of the request that
 * created it — so a Kafka outage never blocks a transfer from being accepted; the event just waits in the
 * table until Kafka is reachable again.
 *
 * <p>{@code eventType} doubles as the Kafka topic name (see {@code common}'s {@code Topics}), and the
 * envelope's {@code eventId} is the outbox row's own id, not a freshly generated one: if a send actually
 * reaches Kafka but the process crashes before {@link OutboxWriteOperations#markPublished} commits, the
 * next poll republishes the very same row with the very same {@code eventId}, so a downstream idempotent
 * consumer (Day 6 onward) recognizes and skips the duplicate instead of processing it twice.
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

	/** One event, one Kafka send, one mark-published — never lets one bad row stop the rest of the batch. */
	private void publishOne(OutboxEvent event) {
		try {
			String envelopeJson = buildEnvelopeJson(event);
			kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), envelopeJson)
					.get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
			writeOperations.markPublished(event.getId());
		}
		catch (Exception ex) {
			// Left unpublished on purpose: the next poll tries again. Kafka being briefly down, or this
			// send timing out, must never lose the event or crash the poller for the rows after it.
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
