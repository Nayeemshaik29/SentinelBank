package com.sentinelbank.partnerbank.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.partnerbank.domain.PartnerBankWriteOperations;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code transfer.initiated}, on two topics: the main one, and its {@code .retry} companion (see
 * {@code KafkaConsumerConfig} for how a message moves from one to the other, and eventually to {@code .dlt},
 * on repeated failure). Both listeners share this one handler: idempotency and retry tier are orthogonal
 * concerns, so there is nothing tier-specific to do differently.
 *
 * <p>Any exception thrown here — a malformed message, a database error — propagates out of the listener
 * method on purpose: that is what tells the container's error handler this delivery failed and to apply
 * its retry/DLT policy, rather than silently acknowledging a message that was never actually processed.
 */
@Component
class TransferInitiatedListener {

	private final ObjectMapper objectMapper;

	private final PartnerBankWriteOperations writeOperations;

	TransferInitiatedListener(ObjectMapper objectMapper, PartnerBankWriteOperations writeOperations) {
		this.objectMapper = objectMapper;
		this.writeOperations = writeOperations;
	}

	@KafkaListener(topics = Topics.TRANSFER_INITIATED, containerFactory = "mainListenerContainerFactory")
	void onMainTopic(String message) throws Exception {
		handle(message);
	}

	// Topics.retry(Topics.TRANSFER_INITIATED); a literal is required here since annotation attributes
	// must be compile-time constants.
	@KafkaListener(topics = "transfer.initiated.retry", containerFactory = "retryListenerContainerFactory")
	void onRetryTopic(String message) throws Exception {
		handle(message);
	}

	private void handle(String message) throws Exception {
		EventEnvelope<TransferInitiatedPayload> envelope = objectMapper.readValue(message,
				objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class,
						TransferInitiatedPayload.class));
		TransferInitiatedPayload payload = envelope.payload();
		writeOperations.processTransferInitiated(payload.transferId(), payload.fromAccountId(),
				payload.toAccountId(), payload.amountMinor(), payload.currency(), envelope.correlationId());
	}
}
