package com.sentinelbank.common.error;

import org.springframework.http.HttpStatus;

/** A business error with an HTTP status and a stable machine-readable code (e.g. INSUFFICIENT_FUNDS). */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
