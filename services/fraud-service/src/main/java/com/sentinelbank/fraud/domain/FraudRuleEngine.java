package com.sentinelbank.fraud.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sentinelbank.fraud.config.FraudProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Four simple, independent rules, run against a transfer that has not been recorded yet. Any number can
 * trigger; all triggered reasons go on the one case, rather than one case per rule.
 *
 * <p>This is detection, not prevention: rules run after the transfer already happened (Day 6), so a
 * flagged transfer is not blocked — see the trade-offs section in the README.
 */
@Component
@EnableConfigurationProperties(FraudProperties.class)
public class FraudRuleEngine {

	private final ProcessedTransferRepository processedTransfers;

	private final FraudProperties properties;

	FraudRuleEngine(ProcessedTransferRepository processedTransfers, FraudProperties properties) {
		this.processedTransfers = processedTransfers;
		this.properties = properties;
	}

	public List<String> evaluate(UUID fromAccountId, String toAccountId, long amountMinor, Instant occurredAt) {
		List<String> reasons = new ArrayList<>();

		if (amountMinor >= properties.largeAmountMinor()) {
			reasons.add("LARGE_AMOUNT");
		}

		if (properties.roundAmountModulus() > 0 && amountMinor % properties.roundAmountModulus() == 0) {
			reasons.add("ROUND_AMOUNT");
		}

		// >= threshold total, counting this transfer itself, so a default of 3 means "this one plus 2
		// others already seen in the window" trips the rule.
		Instant windowStart = occurredAt.minus(properties.velocityWindow());
		long recentCount = processedTransfers.countByFromAccountIdAndOccurredAtAfter(fromAccountId, windowStart);
		if (recentCount + 1 >= properties.velocityThreshold()) {
			reasons.add("VELOCITY");
		}

		if (!processedTransfers.existsByFromAccountIdAndToAccountId(fromAccountId, toAccountId)) {
			reasons.add("NEW_BENEFICIARY");
		}

		return reasons;
	}
}
