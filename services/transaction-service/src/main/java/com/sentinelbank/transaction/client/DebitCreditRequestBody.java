package com.sentinelbank.transaction.client;

record DebitCreditRequestBody(String referenceId, long amountMinor, String description) {
}
