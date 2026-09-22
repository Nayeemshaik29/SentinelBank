package com.sentinelbank.partnerbank.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per {@code transfer.initiated} event this mock partner bank has acted on. {@code transferId} is
 * unique: Kafka's at-least-once delivery can redeliver the same event, and this is what makes crediting it
 * idempotent (see {@link PartnerBankWriteOperations}).
 */
@Entity
@Table(name = "partner_credits")
public class PartnerCredit {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "transfer_id", nullable = false, unique = true)
	private UUID transferId;

	@Column(name = "from_account_id", nullable = false)
	private UUID fromAccountId;

	@Column(name = "to_account_id", nullable = false)
	private String toAccountId;

	@Column(name = "amount_minor", nullable = false)
	private long amountMinor;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CreditOutcome outcome;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PartnerCredit() {
		// for JPA
	}

	public PartnerCredit(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
			String currency, CreditOutcome outcome) {
		this.transferId = transferId;
		this.fromAccountId = fromAccountId;
		this.toAccountId = toAccountId;
		this.amountMinor = amountMinor;
		this.currency = currency;
		this.outcome = outcome;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
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

	public CreditOutcome getOutcome() {
		return outcome;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
