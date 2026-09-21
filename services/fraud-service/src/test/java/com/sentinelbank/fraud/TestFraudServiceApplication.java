package com.sentinelbank.fraud;

import org.springframework.boot.SpringApplication;

public class TestFraudServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(FraudServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
