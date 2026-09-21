package com.sentinelbank.partnerbank;

import org.springframework.boot.SpringApplication;

public class TestPartnerBankServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(PartnerBankServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
