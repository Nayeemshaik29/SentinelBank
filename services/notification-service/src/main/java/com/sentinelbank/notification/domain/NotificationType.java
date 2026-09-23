package com.sentinelbank.notification.domain;

/** Which Kafka topic triggered the notification — also what the email is about. */
public enum NotificationType {
	TRANSFER_COMPLETED,
	TRANSFER_FAILED
}
