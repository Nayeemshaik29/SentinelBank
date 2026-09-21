package com.sentinelbank.auth.service;

import java.util.Locale;
import java.util.UUID;

import com.sentinelbank.auth.token.JwtService;
import com.sentinelbank.auth.token.RefreshTokenService;
import com.sentinelbank.auth.user.Role;
import com.sentinelbank.auth.user.User;
import com.sentinelbank.auth.user.UserRepository;
import com.sentinelbank.auth.web.dto.RegisterRequest;
import com.sentinelbank.auth.web.dto.TokenResponse;
import com.sentinelbank.auth.web.dto.UserResponse;
import com.sentinelbank.common.error.ApiException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final RefreshTokenService refreshTokens;

	AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenService refreshTokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokens = refreshTokens;
	}

	/** Self-service registration always creates a CUSTOMER. Analysts are never created through the API. */
	@Transactional
	public UserResponse register(RegisterRequest request) {
		String email = normalize(request.email());
		if (users.existsByEmail(email)) {
			throw emailTaken();
		}
		User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(),
				Role.CUSTOMER);
		try {
			return UserResponse.from(users.saveAndFlush(user));
		}
		catch (DataIntegrityViolationException ex) {
			// two concurrent registrations for the same email: the unique constraint is the real guard
			throw emailTaken();
		}
	}

	@Transactional
	public TokenResponse login(String email, String password) {
		User user = users.findByEmail(normalize(email))
				.filter(User::isEnabled)
				.filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
						"Invalid email or password"));
		return issueTokens(user);
	}

	/** Exchanges a valid refresh token for a new access token and a new (rotated) refresh token. */
	@Transactional(noRollbackFor = ApiException.class)
	public TokenResponse refresh(String rawRefreshToken) {
		UUID userId = refreshTokens.consume(rawRefreshToken);
		User user = users.findById(userId).filter(User::isEnabled)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
						"Invalid refresh token"));
		return issueTokens(user);
	}

	public void logout(String rawRefreshToken) {
		refreshTokens.revoke(rawRefreshToken);
	}

	@Transactional(readOnly = true)
	public UserResponse profile(UUID userId) {
		return users.findById(userId).map(UserResponse::from)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
	}

	private TokenResponse issueTokens(User user) {
		return new TokenResponse(jwtService.issueAccessToken(user), refreshTokens.issue(user.getId()), "Bearer",
				jwtService.accessTokenTtlSeconds());
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
	}
}
