package com.sentinelbank.transaction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.transaction.service.TransferResultService;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code transfer.completed} and {@code transfer.failed} — partner-bank-service's verdict on each
 * transfer — on both the main topics and their {@code .retry} companions (see {@code KafkaConsumerConfig}).
 * Any exception here propagates on purpose, the same reasoning as partner-bank-service's listener: that is
 * what tells the container this delivery failed and to apply its retry/DLT policy.
 */
@Component
class TransferResultListener {

	private final ObjectMapper objectMapper;

	private final TransferResultService transferResultService;

	TransferResultListener(ObjectMapper objectMapper, TransferResultService transferResultService) {
		this.objectMapper = objectMapper;
		this.transferResultService = transferResultService;
	}

	@KafkaListener(topics = { Topics.TRANSFER_COMPLETED, Topics.TRANSFER_FAILED },
			containerFactory = "mainListenerContainerFactory")
	void onMainTopic(String message) throws Exception {
		handle(message);
	}

	// Literal topic names: Topics.retry(...) is a method call and cannot be used in an annotation
	// attribute, which must be a compile-time constant.
	@KafkaListener(topics = { "transfer.completed.retry", "transfer.failed.retry" },
			containerFactory = "retryListenerContainerFactory")
	void onRetryTopic(String message) throws Exception {
		handle(message);
	}

	private void handle(String message) throws Exception {
		EventEnvelope<TransferResultPayload> envelope = objectMapper.readValue(message,
				objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class,
						TransferResultPayload.class));
		switch (envelope.type()) {
			case Topics.TRANSFER_COMPLETED -> transferResultService.handleCompleted(envelope.payload().transferId());
			case Topics.TRANSFER_FAILED -> transferResultService.handleFailed(envelope.payload().transferId());
			default -> throw new IllegalStateException(
					"Unexpected event type on a transfer-result topic: " + envelope.type());
		}
	}
}
