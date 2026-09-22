package com.sentinelbank.account.domain;

import java.time.Instant;
import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.springframework.http.HttpStatus;

/**
 * {@code @Version} gives every save an optimistic lock: two concurrent debits on the same row will not
 * silently overwrite each other. The loser gets an {@code OptimisticLockingFailureException} and is meant
 * to be retried in a fresh transaction, which is what {@link com.sentinelbank.account.service.AccountService}
 * does.
 */
@Entity
@Table(name = "accounts")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Column(name = "account_number", nullable = false, unique = true)
	private String accountNumber;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "balance_minor", nullable = false)
	private long balanceMinor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Version
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Account() {
		// for JPA
	}

	public Account(UUID ownerId, String accountNumber, String currency, long openingBalanceMinor) {
		this.ownerId = ownerId;
		this.accountNumber = accountNumber;
		this.currency = currency;
		this.balanceMinor = openingBalanceMinor;
		this.status = AccountStatus.ACTIVE;
		this.createdAt = Instant.now();
	}

	/** Spends money out of the account. Only allowed while ACTIVE. */
	public void debit(long amountMinor) {
		if (status != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_ACTIVE",
					"Account is " + status + " and cannot be debited");
		}
		if (amountMinor > balanceMinor) {
			throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Insufficient funds");
		}
		balanceMinor -= amountMinor;
	}

	/**
	 * Adds money to the account. Allowed while ACTIVE or FROZEN: a freeze blocks the customer from
	 * spending, but incoming money (including a compensating refund for a failed transfer) must still
	 * land. Only a CLOSED account refuses credits.
	 */
	public void credit(long amountMinor) {
		if (status == AccountStatus.CLOSED) {
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CLOSED", "Account is closed");
		}
		balanceMinor += amountMinor;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public String getCurrency() {
		return currency;
	}

	public long getBalanceMinor() {
		return balanceMinor;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
