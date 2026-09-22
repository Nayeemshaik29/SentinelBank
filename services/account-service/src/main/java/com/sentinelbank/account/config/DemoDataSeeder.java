package com.sentinelbank.account.config;

import java.util.UUID;

import com.sentinelbank.account.domain.Account;
import com.sentinelbank.account.domain.AccountRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Local-demo convenience: two funded accounts so the ledger can be exercised with curl or Postman before
 * the full register-then-open-account flow exists in the UI (Day 9). Disabled with
 * sentinelbank.seed.enabled=false.
 *
 * <p>{@link #DEMO_OWNER_ID} is a fixed placeholder, not the real id of the demo customer seeded by
 * auth-service: each service's database is seeded independently, and auth's demo user gets a fresh random
 * id on every first start. Once the Angular app drives real registration, real accounts will be opened
 * under the real owner id returned by {@code /auth/register}.
 */
@Component
@EnableConfigurationProperties(DemoDataSeeder.SeedProperties.class)
class DemoDataSeeder implements ApplicationRunner {

	static final UUID DEMO_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	@ConfigurationProperties("sentinelbank.seed")
	record SeedProperties(boolean enabled) {
	}

	private final SeedProperties properties;

	private final AccountRepository accounts;

	DemoDataSeeder(SeedProperties properties, AccountRepository accounts) {
		this.properties = properties;
		this.accounts = accounts;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.enabled() || !accounts.findByOwnerIdOrderByCreatedAtAsc(DEMO_OWNER_ID).isEmpty()) {
			return;
		}
		accounts.save(new Account(DEMO_OWNER_ID, "SB0000000001", "USD", 500_000L));
		accounts.save(new Account(DEMO_OWNER_ID, "SB0000000002", "USD", 1_000_000L));
		log.info("Seeded 2 demo accounts (USD 5,000.00 and USD 10,000.00) for owner {}", DEMO_OWNER_ID);
	}
}
