package com.sentinelbank.aiagent.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * A read-only spending assistant, and nothing else — no write tools exist anywhere in this service (see
 * the README's Day 11 section for the full design). Every answer is grounded in exactly one thing: the
 * masked account/transfer summary {@link SpendingContextBuilder} builds for the caller's own identity. The
 * system prompt tells the model that plainly; {@link OutputValidator} is what actually enforces it against
 * whatever the model says back.
 *
 * <p>Two independent things can go wrong here, and each gets its own honest, specific fallback rather than
 * a generic error: account-service/transaction-service being unreachable (no data to answer from), and
 * Ollama being unreachable or slow (no model to ask) — the exact risk PLAN.md calls out for this day
 * ("make the endpoint work with a stub when Ollama isn't running").
 */
@Service
public class SpendingAssistantService {

	private static final Logger log = LoggerFactory.getLogger(SpendingAssistantService.class);

	private static final String SYSTEM_PROMPT = """
			You are SentinelBank's spending assistant. You are strictly read-only: you cannot move money, \
			open or close accounts, change any setting, or perform any action whatsoever — you can only \
			describe the account and transfer information given to you below.

			Rules you must always follow:
			- Only use the account and transfer summary provided in this conversation. Never invent \
			  accounts, amounts, or transfers that are not listed.
			- If the user asks you to perform an action (send money, cancel a transfer, close an account, \
			  change anything), politely refuse and explain you can only provide information — tell them \
			  to use the app directly for that.
			- Never claim, in any tense, that you have performed, are performing, or will perform an \
			  action. You do not have that capability.
			- Be concise and factual.
			""";

	private final SpendingContextBuilder contextBuilder;

	private final ChatModel chatModel;

	SpendingAssistantService(SpendingContextBuilder contextBuilder, ChatModel chatModel) {
		this.contextBuilder = contextBuilder;
		this.chatModel = chatModel;
	}

	public String ask(UUID userId, String question) {
		String context;
		try {
			context = contextBuilder.buildFor(userId);
		}
		catch (RuntimeException ex) {
			log.warn("Could not fetch account data for the AI assistant", ex);
			return "I couldn't access your account information right now. Please try again in a moment.";
		}

		String userPrompt = context + "\nQuestion: " + question;
		try {
			ChatResponse response = chatModel
					.call(new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt))));
			String raw = response.getResult().getOutput().getText();
			return OutputValidator.validate(raw);
		}
		catch (RuntimeException ex) {
			log.warn("The AI model call failed", ex);
			return "The assistant is temporarily unavailable. Please try again shortly.";
		}
	}
}
