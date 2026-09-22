package com.sentinelbank.fraud.web;

import java.util.List;

import com.sentinelbank.fraud.domain.FraudCaseRepository;
import com.sentinelbank.fraud.web.dto.FraudCaseResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reached through the gateway as {@code /api/fraud/**}, already restricted there to {@code ANALYST} (see
 * api-gateway's SecurityConfig). Every case is visible to every analyst — unlike account or transfer
 * ownership, there is no per-analyst filtering here — so this service does not need to re-check identity
 * itself; the gateway's role check is the whole boundary.
 */
@RestController
@RequestMapping("/fraud")
class FraudCaseController {

	private final FraudCaseRepository fraudCases;

	FraudCaseController(FraudCaseRepository fraudCases) {
		this.fraudCases = fraudCases;
	}

	@GetMapping("/cases")
	List<FraudCaseResponse> listCases() {
		return fraudCases.findAllByOrderByCreatedAtDesc().stream().map(FraudCaseResponse::from).toList();
	}
}
