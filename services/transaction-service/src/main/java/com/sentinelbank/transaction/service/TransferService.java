package com.sentinelbank.transaction.service;

import java.util.List;
import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import com.sentinelbank.transaction.client.AccountServiceClient;
import com.sentinelbank.transaction.client.AccountView;
import com.sentinelbank.transaction.domain.RequestHashes;
import com.sentinelbank.transaction.domain.Transfer;
import com.sentinelbank.transaction.domain.TransferRepository;
import com.sentinelbank.transaction.domain.TransferStatus;
import com.sentinelbank.transaction.domain.TransferWriteOperations;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a transfer end to end, following exactly the steps in the README's sequence diagram:
 *
 * <ol>
 * <li>Replay check: an existing idempotency key returns its transfer unchanged, doing no new work.</li>
 * <li>Ownership check: a call to account-service confirms the caller owns {@code fromAccountId} (a 404
 * there, whether the account doesn't exist or belongs to someone else, becomes our own 404 — nothing is
 * created yet, so a request that can never succeed leaves no record).</li>
 * <li>The transfer is created as PENDING (its own transaction, in {@link TransferWriteOperations}).</li>
 * <li>The debit is attempted (a network call, holding no local transaction open).</li>
 * <li>The outcome — DEBITED with an outbox event, or FAILED with a reason — is recorded (another
 * transaction). Either way the transfer row is now a durable record of what was attempted.</li>
 * </ol>
 *
 * <p>This class is deliberately not itself {@code @Transactional}: each write it makes goes through
 * {@link TransferWriteOperations}, a different bean, so that bean's own transactions each start and commit
 * independently instead of one transaction spanning the account-service network call.
 */
@Service
public class TransferService {

	private final TransferRepository transfers;

	private final TransferWriteOperations writeOperations;

	private final AccountServiceClient accountServiceClient;

	TransferService(TransferRepository transfers, TransferWriteOperations writeOperations,
			AccountServiceClient accountServiceClient) {
		this.transfers = transfers;
		this.writeOperations = writeOperations;
		this.accountServiceClient = accountServiceClient;
	}

	public TransferOutcome create(UUID ownerId, CreateTransferCommand command, String idempotencyKey,
			String correlationId) {
		String requestHash = RequestHashes.of(command.fromAccountId(), command.toAccountId(),
				command.amountMinor());

		Transfer existing = transfers.findByIdempotencyKey(idempotencyKey).orElse(null);
		if (existing != null) {
			requireSameRequest(existing, requestHash);
			return new TransferOutcome(existing, false);
		}

		AccountView account = fetchAccount(command.fromAccountId(), ownerId);

		Transfer pending = writeOperations.createPending(ownerId, command.fromAccountId(), command.toAccountId(),
				account.currency(), command.amountMinor(), idempotencyKey, requestHash);
		if (pending.getStatus() != TransferStatus.PENDING) {
			// Lost the create race to a concurrent request using the same idempotency key; that request's
			// result is authoritative, and we must not attempt a second debit.
			return new TransferOutcome(pending, false);
		}

		try {
			accountServiceClient.debit(command.fromAccountId(), pending.getId().toString(), command.amountMinor(),
					"Transfer " + pending.getId());
			return new TransferOutcome(writeOperations.markDebitedAndEnqueueEvent(pending.getId(), correlationId),
					true);
		}
		catch (RuntimeException ex) {
			ApiException failure = toApiException(ex);
			return new TransferOutcome(writeOperations.markFailed(pending.getId(), failure.getCode(),
					failure.getMessage()), true);
		}
	}

	@Transactional(readOnly = true)
	public List<Transfer> listOwnedBy(UUID ownerId) {
		return transfers.findByOwnerIdOrderByCreatedAtDesc(ownerId);
	}

	@Transactional(readOnly = true)
	public Transfer getVisibleTo(UUID transferId, UUID callerId, boolean callerIsAnalyst) {
		Transfer transfer = transfers.findById(transferId).orElseThrow(TransferService::notFound);
		if (!callerIsAnalyst && !transfer.getOwnerId().equals(callerId)) {
			throw notFound();
		}
		return transfer;
	}

	private AccountView fetchAccount(UUID accountId, UUID ownerId) {
		try {
			return accountServiceClient.getAccount(accountId, ownerId);
		}
		catch (ApiException alreadyOurs) {
			throw alreadyOurs;
		}
		catch (RuntimeException ex) {
			throw toApiException(ex);
		}
	}

	private ApiException toApiException(RuntimeException ex) {
		return (ex instanceof ApiException apiException) ? apiException : accountServiceClient.translateFailure(ex);
	}

	private void requireSameRequest(Transfer existing, String requestHash) {
		if (!existing.getRequestHash().equals(requestHash)) {
			throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
					"This idempotency key was already used for a different transfer request");
		}
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transfer not found");
	}
}
