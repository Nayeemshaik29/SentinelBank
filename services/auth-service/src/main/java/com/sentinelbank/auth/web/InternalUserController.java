package com.sentinelbank.auth.web;

import java.util.UUID;

import com.sentinelbank.auth.user.UserRepository;
import com.sentinelbank.auth.web.dto.UserResponse;
import com.sentinelbank.common.error.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only, added for notification-service (Day 8): once it has resolved a transfer's
 * {@code fromAccountId} to an {@code ownerId} (via account-service's own {@code /internal/**}), it needs
 * this to turn that id into an email address to send to. Same trust model as account-service's internal
 * controller: the gateway only ever routes {@code /api/auth/**}, never {@code /internal/**}, so the network
 * boundary is the only check — no caller-identity header is required or checked here.
 */
@RestController
@RequestMapping("/internal/users")
class InternalUserController {

	private final UserRepository users;

	InternalUserController(UserRepository users) {
		this.users = users;
	}

	@GetMapping("/{userId}")
	UserResponse get(@PathVariable UUID userId) {
		return users.findById(userId)
				.map(UserResponse::from)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
	}
}
