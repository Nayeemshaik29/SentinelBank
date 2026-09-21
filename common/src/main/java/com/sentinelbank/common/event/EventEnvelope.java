package com.sentinelbank.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Wrapper for every Kafka message. {@code eventId} is what idempotent consumers record so a
 * redelivered message is recognised and skipped.
 *
 * @param eventId       unique per event, used for de-duplication
 * @param type          event type, e.g. {@link Topics#TRANSFER_INITIATED}
 * @param aggregateId   business key the event is about (also the Kafka message key, e.g. accountId)
 * @param occurredAt    when the event was created
 * @param correlationId the originating request's correlation ID
 * @param payload       the event body
 */
public record EventEnvelope<T>(UUID eventId, String type, String aggregateId, Instant occurredAt,
		String correlationId, T payload) {

	public static <T> EventEnvelope<T> of(String type, String aggregateId, String correlationId, T payload) {
		return new EventEnvelope<>(UUID.randomUUID(), type, aggregateId, Instant.now(), correlationId, payload);
	}
}
