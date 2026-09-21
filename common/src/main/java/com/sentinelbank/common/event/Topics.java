package com.sentinelbank.common.event;

/**
 * Kafka topic names. Each has a {@code .retry} and a {@code .dlt} companion, created by
 * {@code infra/kafka/create-topics.sh}. Messages are keyed by accountId.
 */
public final class Topics {

	public static final String TRANSFER_INITIATED = "transfer.initiated";

	public static final String TRANSFER_COMPLETED = "transfer.completed";

	public static final String TRANSFER_FAILED = "transfer.failed";

	private Topics() {
	}

	public static String retry(String topic) {
		return topic + ".retry";
	}

	public static String dlt(String topic) {
		return topic + ".dlt";
	}
}
