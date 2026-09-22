package com.sentinelbank.partnerbank.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.sentinelbank.partnerbank.domain.CreditOutcome;
import com.sentinelbank.partnerbank.domain.PartnerCredit;

public record PartnerCreditResponse(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
		String currency, CreditOutcome outcome, Instant createdAt) {

	public static PartnerCreditResponse from(PartnerCredit credit) {
		return new PartnerCreditResponse(credit.getTransferId(), credit.getFromAccountId(),
				credit.getToAccountId(), credit.getAmountMinor(), credit.getCurrency(), credit.getOutcome(),
				credit.getCreatedAt());
	}
}
