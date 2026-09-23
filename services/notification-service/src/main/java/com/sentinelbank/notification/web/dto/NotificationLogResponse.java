package com.sentinelbank.notification.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.sentinelbank.notification.domain.NotificationLog;
import com.sentinelbank.notification.domain.NotificationType;

public record NotificationLogResponse(UUID transferId, String recipientEmail, NotificationType type, Instant sentAt) {

	public static NotificationLogResponse from(NotificationLog log) {
		return new NotificationLogResponse(log.getTransferId(), log.getRecipientEmail(), log.getType(),
				log.getSentAt());
	}
}
