package com.sentinelbank.auth.config;

import com.sentinelbank.auth.user.Role;
import com.sentinelbank.auth.user.User;
import com.sentinelbank.auth.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Local-demo convenience: creates one customer and one analyst on first start so the API and the analyst
 * dashboard can be tried immediately. Disabled with sentinelbank.seed.enabled=false. Analysts can only ever
 * be created this way, never through the public registration endpoint.
 */
@Component
@EnableConfigurationProperties(DemoDataSeeder.SeedProperties.class)
class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	@ConfigurationProperties("sentinelbank.seed")
	record SeedProperties(boolean enabled, String password) {
	}

	private final SeedProperties properties;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	DemoDataSeeder(SeedProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.enabled()) {
			return;
		}
		seed("customer@sentinelbank.dev", "Demo Customer", Role.CUSTOMER);
		seed("analyst@sentinelbank.dev", "Demo Analyst", Role.ANALYST);
	}

	private void seed(String email, String fullName, Role role) {
		if (users.existsByEmail(email)) {
			return;
		}
		User user = new User(email, passwordEncoder.encode(properties.password()), fullName, role);
		user.markKycVerified();
		users.save(user);
		log.info("Seeded demo {} user {}", role, email);
	}
}
