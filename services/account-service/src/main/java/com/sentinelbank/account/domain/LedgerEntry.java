package com.sentinelbank.account.domain;

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
 * One append-only row per debit or credit. Never updated, never deleted: the account balance is a cached
 * total, this table is the source of truth for how it got there.
 *
 * <p>{@code referenceId} is the caller's idempotency key (typically a {@code transactionId}). The database
 * has a unique constraint on {@code (accountId, referenceId, entryType)}, so the same debit or credit can
 * never be recorded twice, however many times the caller retries. A debit and its compensating refund
 * deliberately share one {@code referenceId} but differ in {@code entryType}, so both are allowed.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "account_id", nullable = false)
	private UUID accountId;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false)
	private EntryType entryType;

	@Column(name = "amount_minor", nullable = false)
	private long amountMinor;

	@Column(name = "balance_after", nullable = false)
	private long balanceAfter;

	@Column(name = "reference_id", nullable = false)
	private String referenceId;

	private String description;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LedgerEntry() {
		// for JPA
	}

	public LedgerEntry(UUID accountId, EntryType entryType, long amountMinor, long balanceAfter,
			String referenceId, String description) {
		this.accountId = accountId;
		this.entryType = entryType;
		this.amountMinor = amountMinor;
		this.balanceAfter = balanceAfter;
		this.referenceId = referenceId;
		this.description = description;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public EntryType getEntryType() {
		return entryType;
	}

	public long getAmountMinor() {
		return amountMinor;
	}

	public long getBalanceAfter() {
		return balanceAfter;
	}

	public String getReferenceId() {
		return referenceId;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
