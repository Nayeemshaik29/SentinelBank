package com.sentinelbank.transaction.service;

import java.util.UUID;

import com.sentinelbank.transaction.client.AccountServiceClient;
import com.sentinelbank.transaction.domain.Transfer;
import com.sentinelbank.transaction.domain.TransferRepository;
import com.sentinelbank.transaction.domain.TransferStatus;
import com.sentinelbank.transaction.domain.TransferWriteOperations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The other half of the saga: reacts to what partner-bank-service decided, consuming
 * {@code transfer.completed} and {@code transfer.failed} (see {@code TransferResultListener}).
 *
 * <p>Like {@link TransferService}, this is deliberately not itself {@code @Transactional}: the
 * compensating credit is a network call to account-service, and no local transaction should span it (the
 * same reason {@link TransferWriteOperations} is a separate bean, called before and after that call).
 *
 * <p>Every method here is written to be safe against Kafka redelivering the same message: it reads the
 * transfer's current status first and decides what, if anything, is still left to do, rather than assuming
 * this is the first time the event has been seen.
 */
@Service
public class TransferResultService {

	private static final Logger log = LoggerFactory.getLogger(TransferResultService.class);

	private final TransferRepository transfers;

	private final TransferWriteOperations writeOperations;

	private final AccountServiceClient accountServiceClient;

	TransferResultService(TransferRepository transfers, TransferWriteOperations writeOperations,
			AccountServiceClient accountServiceClient) {
		this.transfers = transfers;
		this.writeOperations = writeOperations;
		this.accountServiceClient = accountServiceClient;
	}

	public void handleCompleted(UUID transferId) {
		Transfer transfer = requireTransfer(transferId, "transfer.completed");
		if (transfer.getStatus() == TransferStatus.COMPLETED) {
			return; // redelivery: already handled
		}
		if (transfer.getStatus() != TransferStatus.DEBITED) {
			log.warn("Ignoring transfer.completed for {}: status is {}, expected DEBITED", transferId,
					transfer.getStatus());
			return;
		}
		writeOperations.markCompleted(transferId);
	}

	/**
	 * DEBITED -&gt; COMPENSATING -&gt; (credit the money back) -&gt; REVERSED. If the credit call fails after
	 * Resilience4j's retries are exhausted, that exception is left to propagate out of this method: the
	 * caller (the Kafka listener) does not catch it, so the container's own error handler treats this
	 * delivery as failed and applies its retry/DLT policy — the transfer stays COMPENSATING, visible via
	 * {@code GET /transfers/{id}}, until a retry (automatic or, eventually, from the DLT) succeeds.
	 */
	public void handleFailed(UUID transferId) {
		Transfer transfer = requireTransfer(transferId, "transfer.failed");
		switch (transfer.getStatus()) {
			case REVERSED -> {
				return; // redelivery: the compensation already completed
			}
			case DEBITED -> transfer = writeOperations.startCompensating(transferId);
			case COMPENSATING -> {
				// resuming after a crash between the credit call and recording REVERSED: fall through and
				// retry both steps. The credit call is safe to repeat (idempotent by transferId, Day 3).
			}
			default -> {
				log.warn("Ignoring transfer.failed for {}: status is {}, expected DEBITED or COMPENSATING",
						transferId, transfer.getStatus());
				return;
			}
		}
		accountServiceClient.credit(transfer.getFromAccountId(), transferId.toString(), transfer.getAmountMinor(),
				"Compensating reversal for transfer " + transferId);
		writeOperations.markReversed(transferId);
	}

	private Transfer requireTransfer(UUID transferId, String eventType) {
		return transfers.findById(transferId)
				.orElseThrow(() -> new IllegalStateException(
						"Received " + eventType + " for unknown transfer " + transferId));
	}
}
