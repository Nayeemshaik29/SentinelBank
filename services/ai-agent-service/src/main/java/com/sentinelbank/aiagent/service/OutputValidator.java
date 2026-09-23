package com.sentinelbank.aiagent.service;

import java.util.regex.Pattern;

/**
 * This assistant has no write tools at all (see the README's Day 11 section) — every fact it has access
 * to comes from {@link SpendingContextBuilder}'s read-only summary. But a local LLM can still
 * <em>hallucinate</em> having done something: confidently claim it moved money, closed an account, or
 * changed a setting, none of which it has any ability to do. This is the last line of defense against
 * that: a plain heuristic scan of the model's own words for first-person claims of having taken, or being
 * about to take, an action — not a guarantee (a determined enough phrasing could still slip past a regex),
 * but a real backstop for the ordinary ways a small model actually gets this wrong.
 */
final class OutputValidator {

	static final String SAFE_FALLBACK =
			"I can only share information about your accounts and transfers — I'm not able to make "
					+ "transfers, change anything, or take any action. Please use the app directly for that.";

	// First-person claim ("I have", "I've", "I will", "I'll", "I'm", "I am") followed, within a short
	// distance, by a verb that describes taking a write action. Deliberately broad rather than an exact
	// phrase list: a hallucination's exact wording is not predictable, but its shape — "I" + a claim of
	// having acted — is.
	private static final Pattern FIRST_PERSON_ACTION_CLAIM = Pattern.compile(
			"\\bI(?:'ve|'m|'ll| have| am| will)\\b[^.!?]{0,40}\\b(transfer(?:red|ring)?|sen[dt](?:ing)?|"
					+ "pai[dy](?:ing)?|process(?:ed|ing)?|mov(?:ed|ing)|initiat(?:ed|ing)|execut(?:ed|ing)|"
					+ "cancel(?:led|ling)?|clos(?:ed|ing)|open(?:ed|ing)|froz(?:e|en)|updat(?:ed|ing)|"
					+ "chang(?:ed|ing)|delet(?:ed|ing)|deposit(?:ed|ing)?|withdr(?:ew|awn|awing))\\b",
			Pattern.CASE_INSENSITIVE);

	// Phrases that describe an action as already done or in progress, without needing the first-person
	// subject to appear right next to the verb — e.g. "Your transfer has been sent", "Payment processing now".
	private static final Pattern PASSIVE_ACTION_CLAIM = Pattern.compile(
			"\\b(payment|transfer|withdrawal|deposit)\\b[^.!?]{0,30}\\b(has been|is being|was)\\b"
					+ "[^.!?]{0,20}\\b(sent|processed|completed|initiated|executed|made)\\b",
			Pattern.CASE_INSENSITIVE);

	private OutputValidator() {
	}

	/** Returns the model's answer unchanged if it looks safe, or {@link #SAFE_FALLBACK} if it reads like a
	 * claim of having taken (or about to take) an action this assistant cannot actually perform. */
	static String validate(String rawAnswer) {
		if (rawAnswer == null || rawAnswer.isBlank()) {
			return SAFE_FALLBACK;
		}
		if (FIRST_PERSON_ACTION_CLAIM.matcher(rawAnswer).find()
				|| PASSIVE_ACTION_CLAIM.matcher(rawAnswer).find()) {
			return SAFE_FALLBACK;
		}
		return rawAnswer;
	}
}
