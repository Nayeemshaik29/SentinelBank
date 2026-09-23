package com.sentinelbank.aiagent.web;

import com.sentinelbank.aiagent.service.SpendingAssistantService;
import com.sentinelbank.aiagent.web.dto.AskRequest;
import com.sentinelbank.aiagent.web.dto.AskResponse;
import com.sentinelbank.common.web.CallerIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reached through the gateway as {@code /api/ai/**}, restricted there to {@code CUSTOMER} — an analyst
 * has no spending of their own to ask about, and this assistant only ever answers about the caller's own
 * accounts (see {@link SpendingAssistantService}). */
@RestController
@RequestMapping("/ai")
class AiAskController {

	private final SpendingAssistantService assistantService;

	AiAskController(SpendingAssistantService assistantService) {
		this.assistantService = assistantService;
	}

	@PostMapping("/ask")
	AskResponse ask(HttpServletRequest request, @Valid @RequestBody AskRequest body) {
		String answer = assistantService.ask(CallerIdentity.requireUserId(request), body.question());
		return new AskResponse(answer);
	}
}
