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

	/**
	 * Strips a trailing {@code .retry} or {@code .dlt}, so a retry-tier consumer can compute the DLT
	 * destination from the topic it is actually reading ({@code transfer.initiated.retry}) without
	 * accidentally producing {@code transfer.initiated.retry.dlt}.
	 */
	public static String base(String topic) {
		if (topic.endsWith(".retry")) {
			return topic.substring(0, topic.length() - ".retry".length());
		}
		if (topic.endsWith(".dlt")) {
			return topic.substring(0, topic.length() - ".dlt".length());
		}
		return topic;
	}
}
