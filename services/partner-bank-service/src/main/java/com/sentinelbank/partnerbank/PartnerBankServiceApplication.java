package com.sentinelbank.partnerbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling drives the outbox publisher (see the outbox package).
@EnableScheduling
@SpringBootApplication
public class PartnerBankServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PartnerBankServiceApplication.class, args);
	}

}
