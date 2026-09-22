package com.sentinelbank.transaction.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

	Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

	List<Transfer> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
