package com.sentinelbank.transaction.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per event this service needs to publish to Kafka. Written in the same database transaction as
 * the state change it describes (see {@link TransferWriteOperations}), so the two can never disagree.
 * {@code publishedAt} stays {@code null} until the Day 5 outbox poller sends it and marks it done.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@Column(nullable = false, columnDefinition = "text")
	private String payload;

	@Column(name = "correlation_id")
	private String correlationId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxEvent() {
		// for JPA
	}

	public OutboxEvent(UUID aggregateId, String eventType, String payload, String correlationId) {
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.correlationId = correlationId;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}
}
