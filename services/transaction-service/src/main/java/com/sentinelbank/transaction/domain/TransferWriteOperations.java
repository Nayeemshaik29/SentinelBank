package com.sentinelbank.transaction.domain;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.common.event.Topics;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three atomic steps of a transfer, each its own database transaction, matching the sequence diagram
 * in the README exactly: PENDING is written and committed before the account-service call happens, and the
 * result of that call is recorded in a separate transaction afterwards. No transaction spans the network
 * call itself, so a slow or hung downstream service never holds a database connection open.
 *
 * <p>These methods are called from {@link com.sentinelbank.transaction.service.TransferService}, a
 * different Spring bean, deliberately: {@code @Transactional} is applied by a proxy around this bean, and
 * that proxy is only invoked on calls that arrive from outside the bean (the same reason
 * {@code LedgerOperations} is a separate bean from {@code AccountService} in account-service).
 */
@Service
public class TransferWriteOperations {

	private final TransferRepository transfers;

	private final OutboxEventRepository outboxEvents;

	private final ObjectMapper objectMapper;

	TransferWriteOperations(TransferRepository transfers, OutboxEventRepository outboxEvents,
			ObjectMapper objectMapper) {
		this.transfers = transfers;
		this.outboxEvents = outboxEvents;
		this.objectMapper = objectMapper;
	}

	/**
	 * Creates the transfer as PENDING. If a concurrent request for the same idempotency key wins the race,
	 * the unique constraint on {@code idempotency_key} makes this throw {@link DataIntegrityViolationException}
	 * instead of silently succeeding twice.
	 *
	 * <p>That exception is deliberately left to propagate rather than caught here: once PostgreSQL rejects
	 * one statement, it marks the whole transaction aborted and refuses every further statement (including
	 * a "let me just look up the existing row instead" query) until an actual rollback happens. Only
	 * letting the exception out of this {@code @Transactional} method triggers that rollback; the caller —
	 * {@link com.sentinelbank.transaction.service.TransferService}, a different bean — then looks the row
	 * up in its own, fresh transaction.
	 */
	@Transactional
	public Transfer createPending(UUID ownerId, UUID fromAccountId, String toAccountId, String currency,
			long amountMinor, String idempotencyKey, String requestHash) {
		return transfers.saveAndFlush(
				new Transfer(idempotencyKey, requestHash, ownerId, fromAccountId, toAccountId, currency,
						amountMinor));
	}

	/** Records a successful debit and, in the same transaction, enqueues the event for it to be published. */
	@Transactional
	public Transfer markDebitedAndEnqueueEvent(UUID transferId, String correlationId) {
		Transfer transfer = transfers.findById(transferId).orElseThrow();
		transfer.markDebited();
		outboxEvents.save(new OutboxEvent(transfer.getId(), Topics.TRANSFER_INITIATED,
				toJson(new TransferInitiatedPayload(transfer.getId(), transfer.getFromAccountId(),
						transfer.getToAccountId(), transfer.getAmountMinor(), transfer.getCurrency())),
				transfer.getFromAccountId().toString(), correlationId));
		return transfer;
	}

	/** Records why the debit did not happen. No outbox event: nothing occurred that other services need to know. */
	@Transactional
	public Transfer markFailed(UUID transferId, String failureCode, String failureDetail) {
		Transfer transfer = transfers.findById(transferId).orElseThrow();
		transfer.markFailed(failureCode, failureDetail);
		return transfer;
	}

	/** Consuming {@code transfer.completed}: DEBITED -&gt; COMPLETED, or a no-op on redelivery. */
	@Transactional
	public Transfer markCompleted(UUID transferId) {
		Transfer transfer = transfers.findById(transferId).orElseThrow();
		transfer.markCompleted();
		return transfer;
	}

	/** Consuming {@code transfer.failed}, step 1: DEBITED -&gt; COMPENSATING, before the compensating credit
	 * is attempted. A no-op if this transfer is not (still) DEBITED. */
	@Transactional
	public Transfer startCompensating(UUID transferId) {
		Transfer transfer = transfers.findById(transferId).orElseThrow();
		transfer.startCompensating();
		return transfer;
	}

	/** Consuming {@code transfer.failed}, step 2: after the compensating credit has succeeded. */
	@Transactional
	public Transfer markReversed(UUID transferId) {
		Transfer transfer = transfers.findById(transferId).orElseThrow();
		transfer.markReversed();
		return transfer;
	}

	private String toJson(Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize outbox payload", ex);
		}
	}
}
