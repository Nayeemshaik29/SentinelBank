package com.sentinelbank.notification.web;

import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import com.sentinelbank.notification.domain.NotificationLogRepository;
import com.sentinelbank.notification.web.dto.NotificationLogResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not reachable through the gateway (see partner-bank-service's identical internal controller for the full
 * reasoning) — for verification only: proving a notification was actually recorded for a given transfer,
 * without needing to check MailHog's own inbox.
 */
@RestController
@RequestMapping("/internal/notifications")
class InternalNotificationController {

	private final NotificationLogRepository notificationLogs;

	InternalNotificationController(NotificationLogRepository notificationLogs) {
		this.notificationLogs = notificationLogs;
	}

	@GetMapping("/{transferId}")
	NotificationLogResponse get(@PathVariable UUID transferId) {
		return notificationLogs.findByTransferId(transferId)
				.map(NotificationLogResponse::from)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
						"No notification recorded for this transfer"));
	}
}
