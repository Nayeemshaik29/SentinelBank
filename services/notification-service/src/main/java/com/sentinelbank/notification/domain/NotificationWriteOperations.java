package com.sentinelbank.notification.domain;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way anything is ever written to {@code notification_log}. Idempotency is by
 * {@code transferId} (unique, see the migration), the same shape as every other idempotent SQL write in
 * this project (account-service's ledger, transaction-service's transfers, partner-bank-service's
 * credits): a redelivered {@code transfer.completed}/{@code transfer.failed} tries to insert the same
 * {@code transferId} again and the unique constraint rejects it.
 *
 * <p>Unlike those other three, this write happens <em>after</em> the side effect (sending the email) rather
 * than before it — see {@link com.sentinelbank.notification.kafka.TransferResultListener} for the full
 * flow. That ordering is a deliberate, honestly-stated trade-off: a crash between the email actually
 * sending and this row committing means a redelivery will find no record, resolve the recipient again, and
 * send a second email. For a notification, "at least once" is an acceptable risk in a way it would never
 * be for a debit or a credit — the cost of getting it wrong is a duplicate email, not duplicated money.
 */
@Service
public class NotificationWriteOperations {

	private static final Logger log = LoggerFactory.getLogger(NotificationWriteOperations.class);

	private final NotificationLogRepository notificationLogs;

	NotificationWriteOperations(NotificationLogRepository notificationLogs) {
		this.notificationLogs = notificationLogs;
	}

	@Transactional
	public void recordSent(UUID transferId, String recipientEmail, NotificationType type) {
		try {
			notificationLogs.save(new NotificationLog(transferId, recipientEmail, type));
			log.info("Recorded {} notification sent to {} for transfer {}", type, recipientEmail, transferId);
		}
		catch (DataIntegrityViolationException alreadyRecorded) {
			// a concurrent or previous attempt already recorded it — fine
		}
	}
}
