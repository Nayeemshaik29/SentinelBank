package com.sentinelbank.fraud.domain;

/** Every case starts OPEN. Deciding a case (CONFIRMED / CLOSED_FALSE_POSITIVE) is a future analyst-facing
 * feature; Day 7 only creates cases and lists them. */
public enum CaseStatus {
	OPEN,
	CONFIRMED,
	CLOSED_FALSE_POSITIVE
}
