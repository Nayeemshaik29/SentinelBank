package com.sentinelbank.account.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.sentinelbank.account.domain.EntryType;
import com.sentinelbank.account.domain.LedgerEntry;

public record LedgerEntryResponse(UUID id, UUID accountId, EntryType entryType, long amountMinor,
		long balanceAfter, String referenceId, String description, Instant createdAt) {

	public static LedgerEntryResponse from(LedgerEntry entry) {
		return new LedgerEntryResponse(entry.getId(), entry.getAccountId(), entry.getEntryType(),
				entry.getAmountMinor(), entry.getBalanceAfter(), entry.getReferenceId(), entry.getDescription(),
				entry.getCreatedAt());
	}
}
