package com.sentinelbank.account.web;

import java.util.UUID;

import com.sentinelbank.account.service.AccountService;
import com.sentinelbank.account.web.dto.DebitCreditRequest;
import com.sentinelbank.account.web.dto.LedgerEntryResponse;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only: debit and credit are invoked directly by transaction-service and
 * partner-bank-service on this service's own port, never through the gateway. The gateway only ever routes
 * {@code /api/accounts/**}, and {@code /internal/**} does not match that route, so no client can reach
 * these endpoints even if they discover the path.
 *
 * <p>There is no caller-identity check here (unlike {@link AccountController}): the trust boundary is the
 * network, not a token. A production deployment would add mTLS or a service-to-service credential; that is
 * out of scope for local development.
 */
@RestController
@RequestMapping("/internal/accounts")
class InternalAccountController {

	private final AccountService accountService;

	InternalAccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping("/{accountId}/debit")
	LedgerEntryResponse debit(@PathVariable UUID accountId, @Valid @RequestBody DebitCreditRequest body) {
		return LedgerEntryResponse
				.from(accountService.debit(accountId, body.referenceId(), body.amountMinor(), body.description()));
	}

	@PostMapping("/{accountId}/credit")
	LedgerEntryResponse credit(@PathVariable UUID accountId, @Valid @RequestBody DebitCreditRequest body) {
		return LedgerEntryResponse
				.from(accountService.credit(accountId, body.referenceId(), body.amountMinor(), body.description()));
	}
}
