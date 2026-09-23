package com.sentinelbank.audit.domain;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One immutable record of one Kafka message this service has ever seen — the complete, append-only story
 * of every {@code transfer.*} event, across all three topics, for as long as this collection exists.
 *
 * <p>There is deliberately no update or delete path anywhere in this service: an audit trail that could be
 * edited after the fact would not be one. {@code _id} is the event's own {@code eventId} (the envelope's,
 * not a freshly generated one), which is what makes this collection idempotent the same way every other
 * consumer in this project is — a redelivery of the exact same event (Kafka's own at-least-once delivery,
 * or an outbox row republished after a crash) has the exact same {@code _id} and is rejected by MongoDB's
 * own uniqueness check rather than appearing twice.
 *
 * <p>{@code payload} is stored generically (whatever shape the envelope's body happened to be) rather than
 * as one of transaction-service's, partner-bank-service's or fraud-service's own typed payload records —
 * an audit trail's job is to preserve what was actually sent, not to understand it, so this service does
 * not need (and deliberately does not keep) a copy of every other service's payload shape.
 */
@Document(collection = "audit_events")
public class AuditEvent {

	@Id
	private String id;

	@Indexed
	private String eventType;

	@Indexed
	private String aggregateId;

	private Instant occurredAt;

	private String correlationId;

	private Map<String, Object> payload;

	private Instant recordedAt;

	protected AuditEvent() {
		// for Spring Data
	}

	public AuditEvent(String eventId, String eventType, String aggregateId, Instant occurredAt,
			String correlationId, Map<String, Object> payload) {
		this.id = eventId;
		this.eventType = eventType;
		this.aggregateId = aggregateId;
		this.occurredAt = occurredAt;
		this.correlationId = correlationId;
		this.payload = payload;
		this.recordedAt = Instant.now();
	}

	public String getId() {
		return id;
	}

	public String getEventType() {
		return eventType;
	}

	public String getAggregateId() {
		return aggregateId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}
}
