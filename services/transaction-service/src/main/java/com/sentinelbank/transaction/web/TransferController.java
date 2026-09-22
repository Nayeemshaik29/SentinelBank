package com.sentinelbank.transaction.web;

import java.util.List;
import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import com.sentinelbank.common.web.CallerIdentity;
import com.sentinelbank.common.web.CorrelationIds;
import com.sentinelbank.transaction.service.CreateTransferCommand;
import com.sentinelbank.transaction.service.TransferOutcome;
import com.sentinelbank.transaction.service.TransferService;
import com.sentinelbank.transaction.web.dto.CreateTransferRequest;
import com.sentinelbank.transaction.web.dto.TransferResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reached through the gateway as {@code /api/transfers/**}, restricted there to CUSTOMER (plus ANALYST reads here). */
@RestController
@RequestMapping("/transfers")
class TransferController {

	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

	private final TransferService transferService;

	TransferController(TransferService transferService) {
		this.transferService = transferService;
	}

	@PostMapping
	ResponseEntity<TransferResponse> create(HttpServletRequest request,
			@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody CreateTransferRequest body) {
		validateIdempotencyKey(idempotencyKey);
		UUID ownerId = CallerIdentity.requireUserId(request);
		String correlationId = request.getHeader(CorrelationIds.HEADER);

		TransferOutcome outcome = transferService.create(ownerId,
				new CreateTransferCommand(body.fromAccountId(), body.toAccountId(), body.amountMinor()),
				idempotencyKey, correlationId);

		HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(TransferResponse.from(outcome.transfer()));
	}

	@GetMapping
	List<TransferResponse> listMine(HttpServletRequest request) {
		return transferService.listOwnedBy(CallerIdentity.requireUserId(request)).stream()
				.map(TransferResponse::from).toList();
	}

	@GetMapping("/{transferId}")
	TransferResponse get(HttpServletRequest request, @PathVariable UUID transferId) {
		return TransferResponse.from(transferService.getVisibleTo(transferId, CallerIdentity.requireUserId(request),
				CallerIdentity.isAnalyst(request)));
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Idempotency-Key must not be blank");
		}
		if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
					"Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
		}
	}
}
