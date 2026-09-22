package com.sentinelbank.partnerbank.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
