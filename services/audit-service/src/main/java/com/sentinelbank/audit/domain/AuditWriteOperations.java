package com.sentinelbank.audit.domain;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * The only way anything is ever written to this collection. There is exactly one write: record the event,
 * once, keyed by its own {@code eventId} — see {@link AuditEvent}'s javadoc for why that alone is enough
 * for idempotency here, unlike fraud-service's two-document dance (an audit record has no second write to
 * coordinate against; there is nothing else it needs to stay consistent with).
 */
@Service
public class AuditWriteOperations {

	private static final Logger log = LoggerFactory.getLogger(AuditWriteOperations.class);

	private final AuditEventRepository auditEvents;

	AuditWriteOperations(AuditEventRepository auditEvents) {
		this.auditEvents = auditEvents;
	}

	public void record(String eventId, String eventType, String aggregateId, Instant occurredAt,
			String correlationId, Map<String, Object> payload) {
		if (auditEvents.existsById(eventId)) {
			return; // already recorded — a redelivery of the same event, not a new one
		}
		try {
			auditEvents.insert(new AuditEvent(eventId, eventType, aggregateId, occurredAt, correlationId, payload));
			log.info("Recorded {} for {}", eventType, aggregateId);
		}
		catch (DuplicateKeyException alreadyRecorded) {
			// a concurrent or previous attempt already recorded it — fine
		}
	}
}
