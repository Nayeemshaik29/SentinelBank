package com.sentinelbank.audit.domain;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

	/** One transfer's complete story, oldest first — initiated, then completed or failed. */
	List<AuditEvent> findAllByAggregateIdOrderByOccurredAtAsc(String aggregateId);

	/** The general "what has been happening" feed, newest first, capped by the caller's page size. */
	List<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
