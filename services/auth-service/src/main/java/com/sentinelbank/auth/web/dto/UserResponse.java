package com.sentinelbank.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.sentinelbank.auth.user.KycStatus;
import com.sentinelbank.auth.user.Role;
import com.sentinelbank.auth.user.User;

public record UserResponse(UUID id, String email, String fullName, Role role, KycStatus kycStatus,
		Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
				user.getKycStatus(), user.getCreatedAt());
	}
}
