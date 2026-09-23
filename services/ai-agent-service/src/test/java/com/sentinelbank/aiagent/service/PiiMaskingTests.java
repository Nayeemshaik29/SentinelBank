package com.sentinelbank.aiagent.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingTests {

	@Test
	void anAccountNumberShowsOnlyItsLastFourDigits() {
		assertThat(PiiMasking.maskAccountNumber("SB1234567890")).isEqualTo("••••••••7890");
	}

	@Test
	void aVeryShortValueIsFullyMasked() {
		assertThat(PiiMasking.maskAccountNumber("AB")).isEqualTo("••••");
	}

	@Test
	void aNullAccountNumberIsFullyMasked() {
		assertThat(PiiMasking.maskAccountNumber(null)).isEqualTo("••••");
	}

	@Test
	void aTransferIdIsShortenedToItsFirstSegment() {
		UUID id = UUID.fromString("f40164d7-8f1b-4972-aaf3-e6241c0fe601");
		assertThat(PiiMasking.shorten(id)).isEqualTo("f40164d7");
	}
}
