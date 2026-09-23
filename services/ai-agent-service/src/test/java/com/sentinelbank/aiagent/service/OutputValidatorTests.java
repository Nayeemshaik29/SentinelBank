package com.sentinelbank.aiagent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputValidatorTests {

	@Test
	void anOrdinaryFactualAnswerPassesThrough() {
		String answer = "You have $4,824.50 in your USD account. Your most recent transfer was $125.50 "
				+ "to PARTNER-DEMO, which completed successfully.";
		assertThat(OutputValidator.validate(answer)).isEqualTo(answer);
	}

	@Test
	void refusingToActPassesThrough() {
		String answer = "I can't send money on your behalf — please use the transfer form in the app.";
		assertThat(OutputValidator.validate(answer)).isEqualTo(answer);
	}

	@Test
	void discussingPastTransfersInThirdPersonIsNotFlagged() {
		// "sent" appears, but describing the user's OWN past transfer (not a first-person claim by the
		// assistant) must not trip the same heuristic that catches a hallucinated first-person claim.
		String answer = "Your transfer to PARTNER-DEMO for $125.50 shows as COMPLETED.";
		assertThat(OutputValidator.validate(answer)).isEqualTo(answer);
	}

	@Test
	void aFirstPersonClaimOfHavingTransferredMoneyIsReplaced() {
		assertFlagged("I have transferred $100 to your friend.");
	}

	@Test
	void aFirstPersonClaimOfHavingSentAPaymentIsReplaced() {
		assertFlagged("I've sent the payment for you.");
	}

	@Test
	void aFirstPersonFutureTenseClaimIsReplaced() {
		assertFlagged("I'll transfer $50 right away.");
	}

	@Test
	void aFirstPersonPresentProgressiveClaimIsReplaced() {
		assertFlagged("I am processing your withdrawal now.");
	}

	@Test
	void aClaimOfHavingClosedAnAccountIsReplaced() {
		assertFlagged("Sure, I have closed that account for you.");
	}

	@Test
	void aClaimOfHavingChangedSettingsIsReplaced() {
		assertFlagged("I have updated your account settings.");
	}

	@Test
	void aPassiveVoiceClaimThatATransferWasSentIsReplaced() {
		assertFlagged("Your transfer has been sent successfully.");
	}

	@Test
	void aPassiveVoiceClaimThatAPaymentWasProcessedIsReplaced() {
		assertFlagged("Your payment was processed just now.");
	}

	@Test
	void aBlankAnswerIsReplacedWithTheSafeFallback() {
		assertThat(OutputValidator.validate("  ")).isEqualTo(OutputValidator.SAFE_FALLBACK);
	}

	@Test
	void aNullAnswerIsReplacedWithTheSafeFallback() {
		assertThat(OutputValidator.validate(null)).isEqualTo(OutputValidator.SAFE_FALLBACK);
	}

	private static void assertFlagged(String hallucination) {
		assertThat(OutputValidator.validate(hallucination)).isEqualTo(OutputValidator.SAFE_FALLBACK);
	}
}
