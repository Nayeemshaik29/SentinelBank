package com.sentinelbank.account.web;

import java.util.List;
import java.util.UUID;

import com.sentinelbank.account.service.AccountService;
import com.sentinelbank.account.web.dto.AccountResponse;
import com.sentinelbank.account.web.dto.CreateAccountRequest;
import com.sentinelbank.account.web.dto.LedgerEntryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing account API, reached through the gateway as {@code /api/accounts/**}. An analyst may view
 * any account (for fraud investigation); a customer may only view their own.
 */
@RestController
@RequestMapping("/accounts")
class AccountController {

	private final AccountService accountService;

	AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping
	List<AccountResponse> listMine(HttpServletRequest request) {
		return accountService.listOwnedBy(CallerContext.userId(request)).stream().map(AccountResponse::from)
				.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse open(HttpServletRequest request, @Valid @RequestBody CreateAccountRequest body) {
		return AccountResponse.from(accountService.open(CallerContext.userId(request), body.currency()));
	}

	@GetMapping("/{accountId}")
	AccountResponse get(HttpServletRequest request, @PathVariable UUID accountId) {
		return AccountResponse.from(accountService.getVisibleTo(accountId, CallerContext.userId(request),
				CallerContext.isAnalyst(request)));
	}

	@GetMapping("/{accountId}/ledger")
	List<LedgerEntryResponse> ledger(HttpServletRequest request, @PathVariable UUID accountId) {
		return accountService
				.ledgerVisibleTo(accountId, CallerContext.userId(request), CallerContext.isAnalyst(request)).stream()
				.map(LedgerEntryResponse::from)
				.toList();
	}
}
