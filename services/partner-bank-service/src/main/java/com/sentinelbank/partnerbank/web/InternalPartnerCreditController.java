package com.sentinelbank.partnerbank.web;

import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import com.sentinelbank.partnerbank.domain.PartnerCreditRepository;
import com.sentinelbank.partnerbank.web.dto.PartnerCreditResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not reachable through the gateway (no route to this service is defined there): a way to check what the
 * mock partner bank did for a given transfer, useful when verifying or demoing the saga.
 */
@RestController
@RequestMapping("/internal/partner-credits")
class InternalPartnerCreditController {

	private final PartnerCreditRepository partnerCredits;

	InternalPartnerCreditController(PartnerCreditRepository partnerCredits) {
		this.partnerCredits = partnerCredits;
	}

	@GetMapping("/{transferId}")
	PartnerCreditResponse get(@PathVariable UUID transferId) {
		return partnerCredits.findByTransferId(transferId).map(PartnerCreditResponse::from)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PARTNER_CREDIT_NOT_FOUND",
						"No partner credit recorded for this transfer"));
	}
}
