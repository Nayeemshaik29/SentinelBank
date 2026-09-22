package com.sentinelbank.fraud.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Everything that happens when a {@code transfer.initiated} event is consumed.
 *
 * <p>This MongoDB is a single instance, not a replica set, so multi-document transactions are not
 * available — unlike the SQL services, this cannot wrap "record the case" and "record that we processed
 * this transfer" in one atomic commit. Idempotency comes from a different, equally solid mechanism instead:
 * both {@link FraudCase} and {@link ProcessedTransfer} use the transfer id as their {@code _id}, so each
 * write is individually safe to repeat (MongoDB rejects a duplicate {@code _id} on its own), and the two
 * writes can be retried in any combination — including a crash between them — until both have succeeded.
 * {@link ProcessedTransfer} is written last on purpose: its absence is exactly what tells a retry "this
 * transfer is not fully handled yet, evaluate and (re)write."
 */
@Service
public class FraudWriteOperations {

	private static final Logger log = LoggerFactory.getLogger(FraudWriteOperations.class);

	private final ProcessedTransferRepository processedTransfers;

	private final FraudCaseRepository fraudCases;

	private final FraudRuleEngine ruleEngine;

	FraudWriteOperations(ProcessedTransferRepository processedTransfers, FraudCaseRepository fraudCases,
			FraudRuleEngine ruleEngine) {
		this.processedTransfers = processedTransfers;
		this.fraudCases = fraudCases;
		this.ruleEngine = ruleEngine;
	}

	public void processTransferInitiated(UUID transferId, UUID fromAccountId, String toAccountId,
			long amountMinor, String currency, Instant occurredAt) {
		if (processedTransfers.existsById(transferId.toString())) {
			return; // fully handled already
		}

		List<String> reasons = ruleEngine.evaluate(fromAccountId, toAccountId, amountMinor, occurredAt);
		if (!reasons.isEmpty()) {
			try {
				fraudCases.insert(new FraudCase(transferId, fromAccountId, toAccountId, amountMinor, currency,
						reasons));
				log.info("Opened fraud case for transfer {} ({})", transferId, reasons);
			}
			catch (DuplicateKeyException alreadyCreated) {
				// a previous, incomplete attempt already created it — fine, keep going
			}
		}

		try {
			processedTransfers.insert(new ProcessedTransfer(transferId, fromAccountId, toAccountId, amountMinor,
					currency, occurredAt));
		}
		catch (DuplicateKeyException alreadyRecorded) {
			// a concurrent or previous attempt already recorded it — fine
		}
	}
}
