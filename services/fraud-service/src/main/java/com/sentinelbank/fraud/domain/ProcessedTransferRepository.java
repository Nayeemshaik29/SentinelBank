package com.sentinelbank.fraud.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedTransferRepository extends MongoRepository<ProcessedTransfer, String> {

	/** Velocity rule: how many transfers this account has sent since {@code since} (not counting this one,
	 * since it is queried before this transfer's own record is written). */
	long countByFromAccountIdAndOccurredAtAfter(UUID fromAccountId, Instant since);

	/** New-beneficiary rule: has this account ever sent to this destination before? */
	boolean existsByFromAccountIdAndToAccountId(UUID fromAccountId, String toAccountId);
}
