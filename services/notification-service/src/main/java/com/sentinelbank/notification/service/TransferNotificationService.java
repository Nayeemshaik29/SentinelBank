package com.sentinelbank.notification.service;

import java.util.Locale;
import java.util.UUID;

import com.sentinelbank.notification.client.AccountServiceClient;
import com.sentinelbank.notification.client.AuthServiceClient;
import com.sentinelbank.notification.client.UserView;
import com.sentinelbank.notification.config.NotificationProperties;
import com.sentinelbank.notification.domain.NotificationLogRepository;
import com.sentinelbank.notification.domain.NotificationType;
import com.sentinelbank.notification.domain.NotificationWriteOperations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Everything that happens when a {@code transfer.completed} or {@code transfer.failed} event is consumed:
 * resolve who to email, send the email, record that it was sent.
 *
 * <p><b>Idempotency check first, as a fast path</b> — {@code notification_log.transfer_id} is unique
 * (Day 8), so a redelivery that already fully succeeded is a cheap no-op here rather than re-resolving the
 * recipient and re-sending mail for nothing. This is a read-then-act check, not itself the source of
 * correctness (the unique constraint in {@link NotificationWriteOperations} is), the same layering
 * fraud-service uses for its own {@code existsById} fast path.
 */
@Service
@EnableConfigurationProperties(NotificationProperties.class)
public class TransferNotificationService {

	private static final Logger log = LoggerFactory.getLogger(TransferNotificationService.class);

	private final NotificationLogRepository notificationLogs;

	private final NotificationWriteOperations writeOperations;

	private final AccountServiceClient accountServiceClient;

	private final AuthServiceClient authServiceClient;

	private final JavaMailSender mailSender;

	private final NotificationProperties properties;

	TransferNotificationService(NotificationLogRepository notificationLogs,
			NotificationWriteOperations writeOperations, AccountServiceClient accountServiceClient,
			AuthServiceClient authServiceClient, JavaMailSender mailSender, NotificationProperties properties) {
		this.notificationLogs = notificationLogs;
		this.writeOperations = writeOperations;
		this.accountServiceClient = accountServiceClient;
		this.authServiceClient = authServiceClient;
		this.mailSender = mailSender;
		this.properties = properties;
	}

	public void notifyOutcome(UUID transferId, UUID fromAccountId, long amountMinor, String currency,
			String reason, NotificationType type) {
		if (notificationLogs.existsByTransferId(transferId)) {
			return; // fully handled already
		}

		UUID ownerId = accountServiceClient.getAccount(fromAccountId).ownerId();
		UserView user = authServiceClient.getUser(ownerId);

		sendEmail(user, transferId, amountMinor, currency, reason, type);
		writeOperations.recordSent(transferId, user.email(), type);
	}

	private void sendEmail(UserView recipient, UUID transferId, long amountMinor, String currency, String reason,
			NotificationType type) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.fromAddress());
		message.setTo(recipient.email());
		message.setSubject(subjectFor(type, transferId));
		message.setText(bodyFor(recipient, type, transferId, amountMinor, currency, reason));
		mailSender.send(message);
		log.info("Sent {} email to {} for transfer {}", type, recipient.email(), transferId);
	}

	private String subjectFor(NotificationType type, UUID transferId) {
		String shortId = transferId.toString().substring(0, 8);
		return switch (type) {
			case TRANSFER_COMPLETED -> "Your transfer " + shortId + " is complete";
			case TRANSFER_FAILED -> "Your transfer " + shortId + " could not be completed";
		};
	}

	private String bodyFor(UserView recipient, NotificationType type, UUID transferId, long amountMinor,
			String currency, String reason) {
		String amount = formatAmount(amountMinor, currency);
		String greeting = "Hi " + recipient.fullName() + ",\n\n";
		return switch (type) {
			case TRANSFER_COMPLETED -> greeting + "Your transfer of " + amount + " (reference " + transferId
					+ ") has completed successfully.\n\nSentinelBank";
			case TRANSFER_FAILED -> greeting + "Your transfer of " + amount + " (reference " + transferId
					+ ") could not be completed" + (reason != null ? " (" + reason + ")" : "")
					+ ", and the amount has been returned to your account.\n\nSentinelBank";
		};
	}

	/** Whole units, two decimal places — good enough for a demo notification; not a currency-formatting
	 * library, and every amount in this project is USD-minor-unit (cents) today anyway. */
	private static String formatAmount(long amountMinor, String currency) {
		return String.format(Locale.US, "%s %,.2f", currency, amountMinor / 100.0);
	}
}
