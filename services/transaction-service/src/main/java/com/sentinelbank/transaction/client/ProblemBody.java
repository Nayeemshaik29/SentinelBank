package com.sentinelbank.transaction.client;

/** The parts of account-service's RFC 9457 error body (see {@code common}'s GlobalExceptionHandler) that matter here. */
record ProblemBody(String code, String detail) {
}
