package com.sentinelbank.account;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.sentinelbank.account.domain.Account;
import com.sentinelbank.account.domain.AccountRepository;
import com.sentinelbank.account.domain.EntryType;
import com.sentinelbank.account.domain.LedgerEntry;
import com.sentinelbank.account.domain.LedgerEntryRepository;
import com.sentinelbank.account.service.AccountService;
import com.sentinelbank.common.error.ApiException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct service-level tests, backed by a real PostgreSQL container, for the two properties the account
 * ledger exists to guarantee: idempotency and correctness under concurrency (PLAN.md's verification
 * steps 4 and 8).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountLedgerServiceTests {

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private UUID openAccount(long openingBalanceMinor) {
		Account account = accountService.open(UUID.randomUUID(), "USD");
		if (openingBalanceMinor > 0) {
			accountService.credit(account.getId(), "opening-balance", openingBalanceMinor, "seed");
		}
		return account.getId();
	}

	@Test
	void debitReducesBalanceAndRecordsALedgerEntry() {
		UUID accountId = openAccount(10_000L);

		LedgerEntry entry = accountService.debit(accountId, "txn-1", 3_000L, "coffee");

		assertThat(entry.getEntryType()).isEqualTo(EntryType.DEBIT);
		assertThat(entry.getBalanceAfter()).isEqualTo(7_000L);
		assertThat(accountRepository.findById(accountId).orElseThrow().getBalanceMinor()).isEqualTo(7_000L);
	}

	@Test
	void repeatingTheSameReferenceIdIsANoOp() {
		UUID accountId = openAccount(10_000L);

		LedgerEntry first = accountService.debit(accountId, "txn-retry", 2_000L, "rent");
		LedgerEntry second = accountService.debit(accountId, "txn-retry", 2_000L, "rent");
		LedgerEntry third = accountService.debit(accountId, "txn-retry", 2_000L, "rent");

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(third.getId()).isEqualTo(first.getId());
		assertThat(accountRepository.findById(accountId).orElseThrow().getBalanceMinor())
				.as("money moved exactly once despite three identical requests")
				.isEqualTo(8_000L);
		assertThat(ledgerEntryRepository.countByAccountIdAndReferenceId(accountId, "txn-retry")).isEqualTo(1);
	}

	@Test
	void aDebitAndItsCompensatingCreditCanShareOneReferenceId() {
		UUID accountId = openAccount(10_000L);

		accountService.debit(accountId, "txn-saga-1", 4_000L, "transfer out");
		accountService.credit(accountId, "txn-saga-1", 4_000L, "compensation: partner bank rejected it");

		assertThat(accountRepository.findById(accountId).orElseThrow().getBalanceMinor()).isEqualTo(10_000L);
		assertThat(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).hasSize(2);
	}

	@Test
	void debitingMoreThanTheBalanceIsRejectedAndNothingChanges() {
		UUID accountId = openAccount(1_000L);

		assertThatThrownBy(() -> accountService.debit(accountId, "txn-over", 5_000L, "too much"))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("code", "INSUFFICIENT_FUNDS");

		assertThat(accountRepository.findById(accountId).orElseThrow().getBalanceMinor()).isEqualTo(1_000L);
		assertThat(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).isEmpty();
	}

	@Test
	void aFrozenAccountRejectsDebitsButStillAcceptsCredits() {
		UUID accountId = openAccount(5_000L);
		freeze(accountId);

		assertThatThrownBy(() -> accountService.debit(accountId, "txn-frozen", 100L, "blocked"))
				.isInstanceOf(ApiException.class)
				.hasFieldOrPropertyWithValue("code", "ACCOUNT_NOT_ACTIVE");

		LedgerEntry refund = accountService.credit(accountId, "txn-refund", 100L, "refund still lands");
		assertThat(refund.getBalanceAfter()).isEqualTo(5_100L);
	}

	@Test
	void concurrentDebitsWithDistinctReferencesAllLandExactlyOnce() throws Exception {
		int threads = 16;
		long amountEach = 100L;
		UUID accountId = openAccount(threads * amountEach);

		runConcurrently(threads, i -> accountService.debit(accountId, "concurrent-" + i, amountEach, "load test"));

		assertThat(accountRepository.findById(accountId).orElseThrow().getBalanceMinor())
				.as("every one of the %d debits was applied exactly once, none lost to a lock conflict", threads)
				.isEqualTo(0L);
		assertThat(ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).hasSize(threads);
	}

	@Test
	void concurrentRetriesOfTheSameReferenceIdStillMoveMoneyOnlyOnce() throws Exception {
		int callers = 16;
		UUID accountId = openAccount(10_000L);

		runConcurrently(callers, i -> accountService.debit(accountId, "same-reference", 500L, "double-click"));

		assertThat(accountRepository.findById(accountId).orElseThrow().getBalanceMinor())
				.as("%d simultaneous callers using the same idempotency key still debit once", callers)
				.isEqualTo(9_500L);
		assertThat(ledgerEntryRepository.countByAccountIdAndReferenceId(accountId, "same-reference")).isEqualTo(1);
	}

	/** Runs {@code task} from {@code count} threads, all released at once, and fails on any exception. */
	private void runConcurrently(int count, java.util.function.IntConsumer task) throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(count);
		ExecutorService pool = Executors.newFixedThreadPool(count);
		AtomicInteger index = new AtomicInteger();
		try {
			Callable<Void> job = () -> {
				int i = index.getAndIncrement();
				barrier.await();
				task.accept(i);
				return null;
			};
			var futures = IntStream.range(0, count).<Callable<Void>>mapToObj(i -> job).map(pool::submit).toList();
			for (Future<Void> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdown();
		}
	}

	private void freeze(UUID accountId) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			var account = accountRepository.findById(accountId).orElseThrow();
			// test-only shortcut: there is no public "freeze" API yet (no fraud service to trigger one),
			// so the status is flipped directly via reflection-free JPA dirty checking through a native update.
			org.springframework.test.util.ReflectionTestUtils.setField(account, "status",
					com.sentinelbank.account.domain.AccountStatus.FROZEN);
			accountRepository.saveAndFlush(account);
		});
	}

	@Test
	void concurrentDebitsNeverExceedAnInsufficientBalance() throws Exception {
		int threads = 10;
		UUID accountId = openAccount(500L); // only enough for 5 of the 10 debits below

		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		CyclicBarrier barrier = new CyclicBarrier(threads);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			var futures = IntStream.range(0, threads).<Callable<Void>>mapToObj(i -> () -> {
				barrier.await();
				try {
					accountService.debit(accountId, "race-" + i, 100L, "overdraw attempt");
					succeeded.incrementAndGet();
				}
				catch (ApiException ex) {
					if (!"INSUFFICIENT_FUNDS".equals(ex.getCode())) {
						throw ex;
					}
					rejected.incrementAndGet();
				}
				return null;
			}).map(pool::submit).toList();
			for (Future<Void> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdown();
		}

		assertThat(succeeded.get()).isEqualTo(5);
		assertThat(rejected.get()).isEqualTo(5);
		assertThat(accountRepository.findById(accountId).orElseThrow().getBalanceMinor())
				.as("balance never went negative under concurrent overdraw attempts")
				.isEqualTo(0L);
	}
}
