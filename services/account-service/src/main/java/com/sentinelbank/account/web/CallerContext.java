package com.sentinelbank.account.web;

import java.util.UUID;

import com.sentinelbank.common.web.CallerIdentity;
import jakarta.servlet.http.HttpServletRequest;

/** Thin, package-local alias for {@link CallerIdentity}, kept so call sites in this package stay short. */
final class CallerContext {

	private CallerContext() {
	}

	static UUID userId(HttpServletRequest request) {
		return CallerIdentity.requireUserId(request);
	}

	static boolean isAnalyst(HttpServletRequest request) {
		return CallerIdentity.isAnalyst(request);
	}
}
