package com.sentinelbank.fraud.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.fraud.domain.FraudWriteOperations;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code transfer.initiated} independently of partner-bank-service (a different consumer group,
 * "fraud-service" — every consumer group gets its own copy of every message, which is exactly what lets
 * fraud detection and settlement happen in parallel off the same event). Same two retry tiers as every
 * other consumer in this project; any exception here propagates on purpose (see
 * partner-bank-service's identically-shaped listener for the full reasoning).
 */
@Component
class TransferInitiatedListener {

	private final ObjectMapper objectMapper;

	private final FraudWriteOperations writeOperations;

	TransferInitiatedListener(ObjectMapper objectMapper, FraudWriteOperations writeOperations) {
		this.objectMapper = objectMapper;
		this.writeOperations = writeOperations;
	}

	@KafkaListener(topics = Topics.TRANSFER_INITIATED, containerFactory = "mainListenerContainerFactory")
	void onMainTopic(String message) throws Exception {
		handle(message);
	}

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
				payload.toAccountId(), payload.amountMinor(), payload.currency(), envelope.occurredAt());
	}
}
