package com.sentinelbank.transaction.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The publish-side counterpart to {@link TransferWriteOperations}: a small, focused write for the outbox
 * poller (see {@code OutboxPublisher}). Kept separate from {@code TransferWriteOperations} because it acts
 * on the outbox aggregate, not the transfer aggregate — and, in a different bean, so its
 * {@code @Transactional} proxy is honoured when called from the poller after a network round trip to Kafka
 * (the same self-invocation reason every other write-operations class in this project is split out).
 */
@Service
public class OutboxWriteOperations {

	private final OutboxEventRepository outboxEvents;

	OutboxWriteOperations(OutboxEventRepository outboxEvents) {
		this.outboxEvents = outboxEvents;
	}

	@Transactional
	public void markPublished(UUID outboxEventId) {
		outboxEvents.findById(outboxEventId).ifPresent(event -> event.markPublished(Instant.now()));
	}
}
