package com.sentinelbank.fraud.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One record per {@code transfer.initiated} event this service has fully processed. {@code id} is set to
 * the transfer id itself (not auto-generated): MongoDB enforces {@code _id} uniqueness natively, so a
 * duplicate insert throws rather than silently succeeding twice — the same idempotency guarantee the SQL
 * services get from a unique constraint, without needing a multi-document transaction (this MongoDB is a
 * single instance, not a replica set, so those are not available here).
 *
 * <p>This collection is also the rule engine's own history: velocity and new-beneficiary rules query it
 * directly, so it doubles as this service's memory of what it has already seen.
 */
@Document(collection = "processed_transfers")
@CompoundIndex(name = "velocity_idx", def = "{'fromAccountId': 1, 'occurredAt': -1}")
@CompoundIndex(name = "beneficiary_idx", def = "{'fromAccountId': 1, 'toAccountId': 1}")
public class ProcessedTransfer {

	@Id
	private String id;

	private UUID fromAccountId;

	private String toAccountId;

	private long amountMinor;

	private String currency;

	private Instant occurredAt;

	private Instant processedAt;

	protected ProcessedTransfer() {
		// for Spring Data
	}

	public ProcessedTransfer(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
			String currency, Instant occurredAt) {
		this.id = transferId.toString();
		this.fromAccountId = fromAccountId;
		this.toAccountId = toAccountId;
		this.amountMinor = amountMinor;
		this.currency = currency;
		this.occurredAt = occurredAt;
		this.processedAt = Instant.now();
	}

	public String getId() {
		return id;
	}

	public UUID getFromAccountId() {
		return fromAccountId;
	}

	public String getToAccountId() {
		return toAccountId;
	}

	public long getAmountMinor() {
		return amountMinor;
	}

	public String getCurrency() {
		return currency;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}
}
