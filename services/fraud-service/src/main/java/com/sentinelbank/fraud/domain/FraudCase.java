package com.sentinelbank.fraud.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Also keyed by the transfer id (like {@link ProcessedTransfer}): at most one case per transfer, and
 * creating it twice (a redelivery racing with itself) is a safe no-op via the same duplicate-{@code _id}
 * mechanism.
 */
@Document(collection = "fraud_cases")
public class FraudCase {

	@Id
	private String id;

	private UUID transferId;

	private UUID fromAccountId;

	private String toAccountId;

	private long amountMinor;

	private String currency;

	private List<String> reasons;

	private CaseStatus status;

	private Instant createdAt;

	protected FraudCase() {
		// for Spring Data
	}

	public FraudCase(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor, String currency,
			List<String> reasons) {
		this.id = transferId.toString();
		this.transferId = transferId;
		this.fromAccountId = fromAccountId;
		this.toAccountId = toAccountId;
		this.amountMinor = amountMinor;
		this.currency = currency;
		this.reasons = reasons;
		this.status = CaseStatus.OPEN;
		this.createdAt = Instant.now();
	}

	public String getId() {
		return id;
	}

	public UUID getTransferId() {
		return transferId;
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

	public List<String> getReasons() {
		return reasons;
	}

	public CaseStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
