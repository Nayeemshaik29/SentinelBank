package com.sentinelbank.transaction.domain;

/**
 * PENDING -&gt; DEBITED -&gt; COMPLETED is the happy path. PENDING -&gt; FAILED means the debit itself never
 * succeeded. DEBITED -&gt; COMPENSATING -&gt; REVERSED (Day 6) is the saga's undo, when the partner bank
 * rejects the credit after our debit already went through.
 */
public enum TransferStatus {
	PENDING,
	DEBITED,
	FAILED,
	COMPLETED,
	COMPENSATING,
	REVERSED
}
