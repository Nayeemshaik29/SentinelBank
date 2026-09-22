package com.sentinelbank.partnerbank.domain;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.partnerbank.config.PartnerBankProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything that happens when a {@code transfer.initiated} event is consumed, as one atomic step (see
 * {@code KafkaConsumerConfig} / the listener for the surrounding retry and idempotency-fast-path handling).
 *
 * <p>The credit outcome is decided by a simple, deliberately visible rule: a {@code toAccountId} starting
 * with the configured prefix (default {@code FAIL-}) is rejected, everything else is credited. This lets
 * the saga's compensation path be exercised on command — for a demo or a test — rather than by chance,
 * without any shared mutable state to coordinate.
 */
@Service
@EnableConfigurationProperties(PartnerBankProperties.class)
public class PartnerBankWriteOperations {

	private final PartnerCreditRepository partnerCredits;

	private final OutboxEventRepository outboxEvents;

	private final ObjectMapper objectMapper;

	private final PartnerBankProperties properties;

	PartnerBankWriteOperations(PartnerCreditRepository partnerCredits, OutboxEventRepository outboxEvents,
			ObjectMapper objectMapper, PartnerBankProperties properties) {
		this.partnerCredits = partnerCredits;
		this.outboxEvents = outboxEvents;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	/**
	 * Idempotent by {@code transferId}: if this transfer was already handled (a redelivery of the same
	 * Kafka message), this is a no-op — nothing is written, and in particular no second result event is
	 * published. Callers should check {@link PartnerCreditRepository#existsByTransferId} first as a cheap
	 * fast path; the unique constraint on {@code transfer_id} is the safety net for a genuine race.
	 */
	@Transactional
	public void processTransferInitiated(UUID transferId, UUID fromAccountId, String toAccountId,
			long amountMinor, String currency, String correlationId) {
		if (partnerCredits.existsByTransferId(transferId)) {
			return;
		}

		CreditOutcome outcome = decideOutcome(toAccountId);
		partnerCredits.save(new PartnerCredit(transferId, fromAccountId, toAccountId, amountMinor, currency,
				outcome));

		String eventType = outcome == CreditOutcome.CREDITED ? Topics.TRANSFER_COMPLETED : Topics.TRANSFER_FAILED;
		String reason = outcome == CreditOutcome.REJECTED
				? "Partner bank rejected the credit to " + toAccountId
				: null;
		TransferResultPayload payload = new TransferResultPayload(transferId, fromAccountId, toAccountId,
				amountMinor, currency, reason);
		// Keyed by fromAccountId, the same key transfer.initiated used, so every event about one account
		// (initiated, then completed or failed) lands on the same partition and is seen in order.
		outboxEvents.save(new OutboxEvent(transferId, eventType, toJson(payload), fromAccountId.toString(),
				correlationId));
	}

	private CreditOutcome decideOutcome(String toAccountId) {
		String prefix = properties.failureTriggerPrefix();
		boolean shouldFail = prefix != null && !prefix.isBlank() && toAccountId != null
				&& toAccountId.startsWith(prefix);
		return shouldFail ? CreditOutcome.REJECTED : CreditOutcome.CREDITED;
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
