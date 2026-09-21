package com.sentinelbank.auth.user;

/** Know-your-customer verification state. New users start as PENDING. */
public enum KycStatus {
	PENDING,
	VERIFIED,
	REJECTED
}
