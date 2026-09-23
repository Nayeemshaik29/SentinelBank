package com.sentinelbank.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.notification.domain.NotificationType;
import com.sentinelbank.notification.service.TransferNotificationService;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code transfer.completed} and {@code transfer.failed} independently of transaction-service and
 * fraud-service (its own consumer group, "notification-service") — a separate copy of every message, so a
 * slow or failing email send never blocks the saga's own completion or fraud detection. Same two retry
 * tiers as every other consumer in this project; any exception here propagates on purpose (see
 * partner-bank-service's identically-shaped listener for the full reasoning).
 */
@Component
class TransferResultListener {

	private final ObjectMapper objectMapper;

	private final TransferNotificationService notificationService;

	TransferResultListener(ObjectMapper objectMapper, TransferNotificationService notificationService) {
		this.objectMapper = objectMapper;
		this.notificationService = notificationService;
	}

	@KafkaListener(topics = { Topics.TRANSFER_COMPLETED, Topics.TRANSFER_FAILED },
			containerFactory = "mainListenerContainerFactory")
	void onMainTopic(String message) throws Exception {
		handle(message);
	}

	@KafkaListener(topics = { "transfer.completed.retry", "transfer.failed.retry" },
			containerFactory = "retryListenerContainerFactory")
	void onRetryTopic(String message) throws Exception {
		handle(message);
	}

	private void handle(String message) throws Exception {
		EventEnvelope<TransferResultPayload> envelope = objectMapper.readValue(message,
				objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class,
						TransferResultPayload.class));
		TransferResultPayload payload = envelope.payload();
		NotificationType type = envelope.type().equals(Topics.TRANSFER_COMPLETED) ? NotificationType.TRANSFER_COMPLETED
				: NotificationType.TRANSFER_FAILED;
		notificationService.notifyOutcome(payload.transferId(), payload.fromAccountId(), payload.amountMinor(),
				payload.currency(), payload.reason(), type);
	}
}
