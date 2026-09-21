package com.sentinelbank.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		// 72 is BCrypt's hard limit: longer passwords would be silently truncated
		@NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password,
		@NotBlank @Size(max = 120) String fullName) {
}
