package com.sentinelbank.transaction.service;

import java.util.UUID;

public record CreateTransferCommand(UUID fromAccountId, String toAccountId, long amountMinor) {
}
