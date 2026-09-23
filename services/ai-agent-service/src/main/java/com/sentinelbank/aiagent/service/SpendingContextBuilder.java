package com.sentinelbank.aiagent.service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.sentinelbank.aiagent.client.AccountServiceClient;
import com.sentinelbank.aiagent.client.AccountView;
import com.sentinelbank.aiagent.client.TransactionServiceClient;
import com.sentinelbank.aiagent.client.TransferView;
import com.sentinelbank.aiagent.config.AiAgentProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * This is the assistant's whole "tool" layer (the read-only {@code getBalance} / {@code getRecentTransactions}
 * from the plan): plain Java calls, not LLM-invoked function tools. The model this service runs against
 * (Ollama's {@code llama3}) does not support tool calling at all — confirmed directly against Ollama's own
 * API, which returns {@code "does not support tools"} for this model — so tool calls are made
 * deterministically, here, before the model ever runs, and their (masked) results are handed to it as
 * plain text context. This is also simply the safer shape for a banking assistant: what data reaches the
 * model is decided by this code, not by what the model decides to ask for.
 */
@Component
@EnableConfigurationProperties(AiAgentProperties.class)
public class SpendingContextBuilder {

	private static final DateTimeFormatter DATE_FORMAT =
			DateTimeFormatter.ofPattern("MMM d", Locale.US).withZone(java.time.ZoneOffset.UTC);

	private final AccountServiceClient accountServiceClient;

	private final TransactionServiceClient transactionServiceClient;

	private final AiAgentProperties properties;

	SpendingContextBuilder(AccountServiceClient accountServiceClient,
			TransactionServiceClient transactionServiceClient, AiAgentProperties properties) {
		this.accountServiceClient = accountServiceClient;
		this.transactionServiceClient = transactionServiceClient;
		this.properties = properties;
	}

	/** Fetches the caller's own accounts and recent transfers and renders them as a masked, plain-text
	 * block for the prompt. Never throws for "no data" — an empty account list is a legitimate answer
	 * ("you have no accounts yet"), not a failure; only a genuine downstream problem propagates, for
	 * {@link SpendingAssistantService} to degrade gracefully from. */
	public String buildFor(UUID userId) {
		List<AccountView> accounts = accountServiceClient.listMine(userId);
		List<TransferView> transfers = transactionServiceClient.listMine(userId);

		StringBuilder context = new StringBuilder();
		context.append("Accounts:\n");
		if (accounts.isEmpty()) {
			context.append("(none)\n");
		}
		for (AccountView account : accounts) {
			context.append("- ").append(PiiMasking.maskAccountNumber(account.accountNumber()))
					.append(" (").append(account.currency()).append("): ")
					.append(formatMoney(account.balanceMinor(), account.currency()))
					.append(", ").append(account.status()).append('\n');
		}

		context.append("\nRecent transfers (most recent first):\n");
		List<TransferView> recent = transfers.stream()
				.sorted(Comparator.comparing(TransferView::createdAt).reversed())
				.limit(properties.maxRecentTransfers())
				.toList();
		if (recent.isEmpty()) {
			context.append("(none)\n");
		}
		for (TransferView transfer : recent) {
			context.append("- ").append(DATE_FORMAT.format(transfer.createdAt())).append(": sent ")
					.append(formatMoney(transfer.amountMinor(), transfer.currency()))
					.append(" to ").append(transfer.toAccountId())
					.append(" (reference ").append(PiiMasking.shorten(transfer.id())).append("), status ")
					.append(transfer.status()).append('\n');
		}

		return context.toString();
	}

	private static String formatMoney(long amountMinor, String currency) {
		return "%s %,.2f".formatted(currency, amountMinor / 100.0);
	}
}
