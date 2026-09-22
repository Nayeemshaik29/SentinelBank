package com.sentinelbank.transaction.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	// Used by the Day 5 outbox publisher, which polls for unpublished rows.
	List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();
}
