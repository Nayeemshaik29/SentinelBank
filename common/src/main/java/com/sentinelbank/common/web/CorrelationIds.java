package com.sentinelbank.common.web;

import java.util.UUID;

/** Names and helpers for the correlation ID that ties one user request together across services. */
public final class CorrelationIds {

	/** HTTP header and Kafka header carrying the correlation ID. */
	public static final String HEADER = "X-Correlation-Id";

	/** Key used in the logging MDC so every log line can print the ID. */
	public static final String MDC_KEY = "correlationId";

	private CorrelationIds() {
	}

	public static String newId() {
		return UUID.randomUUID().toString();
	}
}
