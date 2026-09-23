package com.sentinelbank.audit.kafka;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelbank.audit.domain.AuditWriteOperations;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes every {@code transfer.*} topic, in its own consumer group ("audit-service") — a separate copy
 * of every message from whatever partner-bank-service, transaction-service and fraud-service each
 * consume, which is exactly what lets this be a complete, independent trail rather than a side effect of
 * any one of them.
 *
 * <p>Deliberately does not deserialize into a typed payload record the way every other consumer in this
 * project does: an audit trail's job is to preserve what was actually sent, not to interpret it, so the
 * payload is read as a generic {@code Map} and stored as-is (see {@link com.sentinelbank.audit.domain.AuditEvent}).
 * This is also what lets one listener method cover all three topics without three payload types.
 */
@Component
class AuditEventListener {

	private final ObjectMapper objectMapper;

	private final AuditWriteOperations writeOperations;

	AuditEventListener(ObjectMapper objectMapper, AuditWriteOperations writeOperations) {
		this.objectMapper = objectMapper;
		this.writeOperations = writeOperations;
	}

	@KafkaListener(topics = { Topics.TRANSFER_INITIATED, Topics.TRANSFER_COMPLETED, Topics.TRANSFER_FAILED },
			containerFactory = "mainListenerContainerFactory")
	void onMainTopic(String message) throws Exception {
		handle(message);
	}

	@KafkaListener(
			topics = { "transfer.initiated.retry", "transfer.completed.retry", "transfer.failed.retry" },
			containerFactory = "retryListenerContainerFactory")
	void onRetryTopic(String message) throws Exception {
		handle(message);
	}

	@SuppressWarnings("unchecked")
	private void handle(String message) throws Exception {
		EventEnvelope<Map<String, Object>> envelope = objectMapper.readValue(message,
				objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, Map.class));
		writeOperations.record(envelope.eventId().toString(), envelope.type(), envelope.aggregateId(),
				envelope.occurredAt(), envelope.correlationId(), envelope.payload());
	}
}
