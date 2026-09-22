package com.sentinelbank.account.domain;

import java.util.UUID;

import com.sentinelbank.common.error.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The atomic core of a debit or credit: one database transaction that is either idempotent (a replay
 * of an already-applied reference) or safely rejected as a whole.
 *
 * <p>Each attempt runs in its own transaction (this class has no retry logic; see
 * {@link com.sentinelbank.account.service.AccountService} for that), so a failure here always rolls back
 * cleanly:
 * <ul>
 * <li>If the {@code (accountId, referenceId, entryType)} triple was already recorded, that entry is
 * returned unchanged. Nothing is written again.</li>
 * <li>Otherwise the balance is mutated and the new ledger row is inserted in the same transaction. If two
 * concurrent calls race for the same reference, the loser's insert fails on the unique constraint, which
 * rolls back its balance change too — the caller then retries and finds the winner's entry via the
 * idempotency check above.</li>
 * <li>If the account was changed by someone else since it was read (a concurrent unrelated debit), the
 * save fails with an optimistic-locking exception, and the whole transaction rolls back.</li>
 * </ul>
 */
@Service
public class LedgerOperations {

	private final AccountRepository accounts;

	private final LedgerEntryRepository ledgerEntries;

	LedgerOperations(AccountRepository accounts, LedgerEntryRepository ledgerEntries) {
		this.accounts = accounts;
		this.ledgerEntries = ledgerEntries;
	}

	@Transactional
	public LedgerEntry debit(UUID accountId, String referenceId, long amountMinor, String description) {
		return apply(accountId, referenceId, EntryType.DEBIT, amountMinor, description);
	}

	@Transactional
	public LedgerEntry credit(UUID accountId, String referenceId, long amountMinor, String description) {
		return apply(accountId, referenceId, EntryType.CREDIT, amountMinor, description);
	}

	private LedgerEntry apply(UUID accountId, String referenceId, EntryType type, long amountMinor,
			String description) {
		var existing = ledgerEntries.findByAccountIdAndReferenceIdAndEntryType(accountId, referenceId, type);
		if (existing.isPresent()) {
			return existing.get();
		}

		Account account = accounts.findById(accountId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found"));
		if (type == EntryType.DEBIT) {
			account.debit(amountMinor);
		}
		else {
			account.credit(amountMinor);
		}
		// Flushed now, not just at commit, so an optimistic-lock conflict surfaces here rather than
		// silently at the end of the transaction.
		accounts.saveAndFlush(account);

		LedgerEntry entry = new LedgerEntry(accountId, type, amountMinor, account.getBalanceMinor(), referenceId,
				description);
		return ledgerEntries.saveAndFlush(entry);
	}
}
