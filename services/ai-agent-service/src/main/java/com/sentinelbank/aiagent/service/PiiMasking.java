package com.sentinelbank.aiagent.service;

import java.util.UUID;

/**
 * Masks the identifiers that reach the model. Nothing here ever sends a real account number or a full
 * internal id into a prompt — a local LLM is still a third party from the data's point of view, and a
 * masked value is everything an answer about "my balance" or "my recent transfers" actually needs.
 */
final class PiiMasking {

	private PiiMasking() {
	}

	/** {@code SB1234567890} -&gt; {@code SB••••••7890}: the last 4 digits are enough for a customer to
	 * recognize their own account in a sentence; the rest never needs to leave account-service. */
	static String maskAccountNumber(String accountNumber) {
		if (accountNumber == null || accountNumber.length() <= 4) {
			return "••••";
		}
		int visible = 4;
		String tail = accountNumber.substring(accountNumber.length() - visible);
		return "•".repeat(accountNumber.length() - visible) + tail;
	}

	/** A transfer id is not personally identifying, but a full UUID is unreadable and burns prompt tokens
	 * for nothing a spending question needs — shortened the same way the Angular app already displays
	 * transfer ids to a human. */
	static String shorten(UUID id) {
		return id.toString().substring(0, 8);
	}
}
