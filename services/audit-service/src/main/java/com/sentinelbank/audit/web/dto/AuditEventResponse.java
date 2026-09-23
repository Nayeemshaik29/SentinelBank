package com.sentinelbank.audit.web.dto;

import java.time.Instant;
import java.util.Map;

import com.sentinelbank.audit.domain.AuditEvent;

public record AuditEventResponse(String eventId, String eventType, String aggregateId, Instant occurredAt,
		String correlationId, Map<String, Object> payload) {

	public static AuditEventResponse from(AuditEvent event) {
		return new AuditEventResponse(event.getId(), event.getEventType(), event.getAggregateId(),
				event.getOccurredAt(), event.getCorrelationId(), event.getPayload());
	}
}
