package com.sentinelbank.fraud.domain;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FraudCaseRepository extends MongoRepository<FraudCase, String> {

	List<FraudCase> findAllByOrderByCreatedAtDesc();
}
