package com.sentinelbank.account;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountApiTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void aCustomerCanOpenAndListTheirOwnAccounts() throws Exception {
		String userId = UUID.randomUUID().toString();

		mvc.perform(post("/accounts").header("X-User-Id", userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"currency\":\"USD\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerId").value(userId))
				.andExpect(jsonPath("$.currency").value("USD"))
				.andExpect(jsonPath("$.balanceMinor").value(0))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.matchesPattern("SB\\d{10}")));

		mvc.perform(get("/accounts").header("X-User-Id", userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void invalidCurrencyIsRejected() throws Exception {
		mvc.perform(post("/accounts").header("X-User-Id", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"us-dollars\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fields.currency").exists());
	}

	@Test
	void requestsWithoutTheGatewaysIdentityHeaderAreRejected() throws Exception {
		mvc.perform(get("/accounts")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("MISSING_IDENTITY"));
	}

	@Test
	void aCustomerCannotSeeSomeoneElsesAccountButAnAnalystCan() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		String accountId = openAccount(ownerId);

		mvc.perform(get("/accounts/" + accountId).header("X-User-Id", UUID.randomUUID().toString()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

		mvc.perform(get("/accounts/" + accountId).header("X-User-Id", UUID.randomUUID().toString())
				.header("X-User-Roles", "ANALYST"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(accountId));
	}

	@Test
	void internalDebitAndCreditWorkAndAreRepeatableSafely() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		String accountId = openAccount(ownerId);
		mvc.perform(post("/internal/accounts/" + accountId + "/credit").contentType(MediaType.APPLICATION_JSON)
				.content("{\"referenceId\":\"seed\",\"amountMinor\":10000,\"description\":\"opening\"}"))
				.andExpect(status().isOk());

		mvc.perform(post("/internal/accounts/" + accountId + "/debit").contentType(MediaType.APPLICATION_JSON)
				.content("{\"referenceId\":\"txn-9\",\"amountMinor\":2500,\"description\":\"groceries\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entryType").value("DEBIT"))
				.andExpect(jsonPath("$.balanceAfter").value(7500));

		// same reference id again: same result, not a second debit
		mvc.perform(post("/internal/accounts/" + accountId + "/debit").contentType(MediaType.APPLICATION_JSON)
				.content("{\"referenceId\":\"txn-9\",\"amountMinor\":2500,\"description\":\"groceries\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balanceAfter").value(7500));

		mvc.perform(get("/accounts/" + accountId + "/ledger").header("X-User-Id", ownerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void debitingMoreThanTheBalanceReturnsAClearConflict() throws Exception {
		String ownerId = UUID.randomUUID().toString();
		String accountId = openAccount(ownerId);

		mvc.perform(post("/internal/accounts/" + accountId + "/debit").contentType(MediaType.APPLICATION_JSON)
				.content("{\"referenceId\":\"txn-over\",\"amountMinor\":100,\"description\":\"too much\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
	}

	@Test
	void aNonPositiveAmountIsRejectedByValidation() throws Exception {
		String accountId = openAccount(UUID.randomUUID().toString());

		mvc.perform(post("/internal/accounts/" + accountId + "/debit").contentType(MediaType.APPLICATION_JSON)
				.content("{\"referenceId\":\"txn-bad\",\"amountMinor\":0,\"description\":\"zero\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void debitingAnUnknownAccountIs404() throws Exception {
		mvc.perform(post("/internal/accounts/" + UUID.randomUUID() + "/debit").contentType(MediaType.APPLICATION_JSON)
				.content("{\"referenceId\":\"txn-x\",\"amountMinor\":100,\"description\":\"?\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
	}

	private String openAccount(String ownerId) throws Exception {
		String body = mvc.perform(post("/accounts").header("X-User-Id", ownerId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"USD\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(body, "$.id");
	}
}
