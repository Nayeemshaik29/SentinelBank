package com.sentinelbank.account.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.sentinelbank.account.domain.Account;
import com.sentinelbank.account.domain.AccountStatus;

public record AccountResponse(UUID id, UUID ownerId, String accountNumber, String currency, long balanceMinor,
		AccountStatus status, Instant createdAt) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(account.getId(), account.getOwnerId(), account.getAccountNumber(),
				account.getCurrency(), account.getBalanceMinor(), account.getStatus(), account.getCreatedAt());
	}
}
