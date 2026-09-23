package com.sentinelbank.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per transfer this service has notified the customer about. {@code transferId} is unique (see the
 * migration): that is what makes the consumer in {@link com.sentinelbank.notification.kafka.TransferResultListener}
 * idempotent — see {@link NotificationWriteOperations} for the full reasoning, including the one honestly
 * documented trade-off this design makes (a crash between sending the email and this row committing can
 * cause a duplicate email on redelivery, which is an acceptable risk for a notification in a way it would
 * not be for money movement).
 */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "transfer_id", nullable = false, unique = true)
	private UUID transferId;

	@Column(name = "recipient_email", nullable = false)
	private String recipientEmail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	protected NotificationLog() {
		// for JPA
	}

	public NotificationLog(UUID transferId, String recipientEmail, NotificationType type) {
		this.transferId = transferId;
		this.recipientEmail = recipientEmail;
		this.type = type;
		this.sentAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTransferId() {
		return transferId;
	}

	public String getRecipientEmail() {
		return recipientEmail;
	}

	public NotificationType getType() {
		return type;
	}

	public Instant getSentAt() {
		return sentAt;
	}
}
