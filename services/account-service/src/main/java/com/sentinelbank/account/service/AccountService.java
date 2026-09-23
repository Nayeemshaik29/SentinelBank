package com.sentinelbank.account.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.sentinelbank.account.domain.Account;
import com.sentinelbank.account.domain.AccountRepository;
import com.sentinelbank.account.domain.LedgerEntry;
import com.sentinelbank.account.domain.LedgerEntryRepository;
import com.sentinelbank.account.domain.LedgerOperations;
import com.sentinelbank.common.error.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

	private static final Logger log = LoggerFactory.getLogger(AccountService.class);

	/**
	 * Retries under contention. {@link LedgerOperations} already made each attempt atomic; this only
	 * re-invokes it in a fresh transaction when a concurrent update got there first.
	 */
	private static final int MAX_ATTEMPTS = 10;

	private final AccountRepository accounts;

	private final LedgerEntryRepository ledgerEntries;

	private final LedgerOperations ledgerOperations;

	AccountService(AccountRepository accounts, LedgerEntryRepository ledgerEntries,
			LedgerOperations ledgerOperations) {
		this.accounts = accounts;
		this.ledgerEntries = ledgerEntries;
		this.ledgerOperations = ledgerOperations;
	}

	public LedgerEntry debit(UUID accountId, String referenceId, long amountMinor, String description) {
		return withRetry(() -> ledgerOperations.debit(accountId, referenceId, amountMinor, description));
	}

	public LedgerEntry credit(UUID accountId, String referenceId, long amountMinor, String description) {
		return withRetry(() -> ledgerOperations.credit(accountId, referenceId, amountMinor, description));
	}

	/**
	 * Retries on {@link OptimisticLockingFailureException} (someone else updated the account first) and on
	 * {@link DataIntegrityViolationException} (a concurrent call for the same reference won the unique-
	 * constraint race). Both are transient by nature: the next attempt's idempotency check or fresh read
	 * resolves them without any special-casing.
	 */
	private LedgerEntry withRetry(Supplier<LedgerEntry> attempt) {
		for (int attemptNumber = 1; attemptNumber <= MAX_ATTEMPTS; attemptNumber++) {
			try {
				return attempt.get();
			}
			catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
				if (attemptNumber == MAX_ATTEMPTS) {
					log.warn("Giving up after {} attempts due to contention", MAX_ATTEMPTS, ex);
					throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_UPDATE_FAILED",
							"Could not complete the operation under heavy contention, please retry");
				}
				jitterSleep();
			}
		}
		throw new IllegalStateException("unreachable");
	}

	private static void jitterSleep() {
		try {
			Thread.sleep(ThreadLocalRandom.current().nextInt(1, 8));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@Transactional
	public Account open(UUID ownerId, String currency) {
		return accounts.save(new Account(ownerId, generateUniqueAccountNumber(), currency, 0L));
	}

	@Transactional(readOnly = true)
	public List<Account> listOwnedBy(UUID ownerId) {
		return accounts.findByOwnerIdOrderByCreatedAtAsc(ownerId);
	}

	@Transactional(readOnly = true)
	public Account getVisibleTo(UUID accountId, UUID callerId, boolean callerIsAnalyst) {
		Account account = accounts.findById(accountId).orElseThrow(AccountService::notFound);
		if (!callerIsAnalyst && !account.getOwnerId().equals(callerId)) {
			// 404, not 403: a non-owner should not be able to tell a real account apart from a missing one.
			throw notFound();
		}
		return account;
	}

	/**
	 * No ownership check, unlike {@link #getVisibleTo}: this is for service-to-service callers on the
	 * {@code /internal/**} path (see {@code InternalAccountController}), which is trusted by network
	 * boundary rather than by caller identity — the same trust model as {@code debit}/{@code credit}.
	 */
	@Transactional(readOnly = true)
	public Account getById(UUID accountId) {
		return accounts.findById(accountId).orElseThrow(AccountService::notFound);
	}

	@Transactional(readOnly = true)
	public List<LedgerEntry> ledgerVisibleTo(UUID accountId, UUID callerId, boolean callerIsAnalyst) {
		getVisibleTo(accountId, callerId, callerIsAnalyst); // ownership check, result unused
		return ledgerEntries.findByAccountIdOrderByCreatedAtDesc(accountId);
	}

	private String generateUniqueAccountNumber() {
		for (int i = 0; i < 5; i++) {
			String candidate = "SB" + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
			if (!accounts.existsByAccountNumber(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not generate a unique account number after 5 attempts");
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found");
	}
}
