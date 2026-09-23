package com.sentinelbank.audit.web;

import java.util.List;

import com.sentinelbank.audit.domain.AuditEventRepository;
import com.sentinelbank.audit.web.dto.AuditEventResponse;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only, on purpose: there is no update or delete endpoint anywhere in this service (see
 * {@link com.sentinelbank.audit.domain.AuditEvent}'s javadoc). Reached through the gateway as
 * {@code /api/audit/**}, restricted there to {@code ANALYST} — the same back-office access model as
 * fraud-service's cases, and for the same reason: this is operational/compliance data, not something a
 * customer's own token should be able to browse.
 */
@RestController
@RequestMapping("/audit")
class AuditEventController {

	private final AuditEventRepository auditEvents;

	AuditEventController(AuditEventRepository auditEvents) {
		this.auditEvents = auditEvents;
	}

	/** The general feed: the most recent {@code limit} events across every transfer, newest first. */
	@GetMapping("/events")
	List<AuditEventResponse> recent(@RequestParam(defaultValue = "100") int limit) {
		return auditEvents.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit)).stream()
				.map(AuditEventResponse::from).toList();
	}

	/** One transfer's complete story, in the order it actually happened. */
	@GetMapping("/events/{aggregateId}")
	List<AuditEventResponse> forAggregate(@PathVariable String aggregateId) {
		return auditEvents.findAllByAggregateIdOrderByOccurredAtAsc(aggregateId).stream()
				.map(AuditEventResponse::from).toList();
	}
}
