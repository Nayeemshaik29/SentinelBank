package com.sentinelbank.partnerbank.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The same transactional outbox pattern as transaction-service (see that service's {@code OutboxEvent} for
 * the full rationale): written in the same transaction as the business row it describes, published by a
 * separate scheduled poller, never the other way around.
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

	@Column(name = "partition_key", nullable = false)
	private String partitionKey;

	@Column(name = "correlation_id")
	private String correlationId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxEvent() {
		// for JPA
	}

	public OutboxEvent(UUID aggregateId, String eventType, String payload, String partitionKey,
			String correlationId) {
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.partitionKey = partitionKey;
		this.correlationId = correlationId;
		this.createdAt = Instant.now();
	}

	public void markPublished(Instant publishedAt) {
		this.publishedAt = publishedAt;
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

	public String getPartitionKey() {
		return partitionKey;
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
