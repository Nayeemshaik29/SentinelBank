package com.sentinelbank.fraud.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sentinelbank.fraud.domain.CaseStatus;
import com.sentinelbank.fraud.domain.FraudCase;

public record FraudCaseResponse(UUID transferId, UUID fromAccountId, String toAccountId, long amountMinor,
		String currency, List<String> reasons, CaseStatus status, Instant createdAt) {

	public static FraudCaseResponse from(FraudCase fraudCase) {
		return new FraudCaseResponse(fraudCase.getTransferId(), fraudCase.getFromAccountId(),
				fraudCase.getToAccountId(), fraudCase.getAmountMinor(), fraudCase.getCurrency(),
				fraudCase.getReasons(), fraudCase.getStatus(), fraudCase.getCreatedAt());
	}
}
