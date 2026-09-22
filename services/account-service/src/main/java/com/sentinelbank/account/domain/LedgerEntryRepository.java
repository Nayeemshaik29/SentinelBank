package com.sentinelbank.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	Optional<LedgerEntry> findByAccountIdAndReferenceIdAndEntryType(UUID accountId, String referenceId,
			EntryType entryType);

	List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

	long countByAccountIdAndReferenceId(UUID accountId, String referenceId);
}
