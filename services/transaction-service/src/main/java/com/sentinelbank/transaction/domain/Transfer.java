package com.sentinelbank.transaction.domain;

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

@Entity
@Table(name = "transfers")
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "idempotency_key", nullable = false, unique = true)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false)
	private String requestHash;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Column(name = "from_account_id", nullable = false)
	private UUID fromAccountId;

	@Column(name = "to_account_id", nullable = false)
	private String toAccountId;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "amount_minor", nullable = false)
	private long amountMinor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransferStatus status;

	@Column(name = "failure_code")
	private String failureCode;

	@Column(name = "failure_detail")
	private String failureDetail;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Transfer() {
		// for JPA
	}

	public Transfer(String idempotencyKey, String requestHash, UUID ownerId, UUID fromAccountId,
			String toAccountId, String currency, long amountMinor) {
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.ownerId = ownerId;
		this.fromAccountId = fromAccountId;
		this.toAccountId = toAccountId;
		this.currency = currency;
		this.amountMinor = amountMinor;
		this.status = TransferStatus.PENDING;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void markDebited() {
		this.status = TransferStatus.DEBITED;
		this.updatedAt = Instant.now();
	}

	public void markFailed(String failureCode, String failureDetail) {
		this.status = TransferStatus.FAILED;
		this.failureCode = failureCode;
		this.failureDetail = failureDetail;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public UUID getFromAccountId() {
		return fromAccountId;
	}

	public String getToAccountId() {
		return toAccountId;
	}

	public String getCurrency() {
		return currency;
	}

	public long getAmountMinor() {
		return amountMinor;
	}

	public TransferStatus getStatus() {
		return status;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public String getFailureDetail() {
		return failureDetail;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
