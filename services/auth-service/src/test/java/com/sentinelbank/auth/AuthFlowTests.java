package com.sentinelbank.auth;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTests {

	private static final String PASSWORD = "Str0ng-Passw0rd";

	@Autowired
	private MockMvc mvc;

	@Test
	void registerLoginAndReadProfile() throws Exception {
		String email = uniqueEmail();

		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email, PASSWORD, "Test User")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.kycStatus").value("PENDING"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		String accessToken = accessTokenOf(login(email, PASSWORD));

		mvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	@Test
	void accessTokenCarriesTheRolesClaimForTheGateway() throws Exception {
		String email = uniqueEmail();
		register(email);

		String accessToken = accessTokenOf(login(email, PASSWORD));

		String payload = new String(java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
		java.util.List<String> roles = JsonPath.read(payload, "$.roles");
		assertThat(roles).containsExactly("CUSTOMER");
		assertThat((String) JsonPath.read(payload, "$.iss")).isEqualTo("sentinelbank");
		assertThat((String) JsonPath.read(payload, "$.email")).isEqualTo(email);
	}

	@Test
	void profileWithoutATokenIsUnauthorized() throws Exception {
		mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void duplicateEmailIsRejectedEvenWithDifferentCase() throws Exception {
		String email = uniqueEmail();
		register(email);

		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email.toUpperCase(), PASSWORD, "Someone Else")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void weakPasswordIsRejectedWithFieldErrors() throws Exception {
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(uniqueEmail(), "short", "Test User")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fields.password").exists());
	}

	@Test
	void malformedJsonIsAClientErrorNotAServerError() throws Exception {
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void wrongPasswordAndUnknownUserGiveTheSameAnswer() throws Exception {
		String email = uniqueEmail();
		register(email);

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(email, "Wrong-Passw0rd")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(uniqueEmail(), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void refreshTokenIsRotatedAndReuseRevokesEverySession() throws Exception {
		String email = uniqueEmail();
		register(email);
		String first = refreshTokenOf(login(email, PASSWORD));

		String second = refreshTokenOf(refresh(first, 200));
		assertThat(second).isNotEqualTo(first);

		// the old token is single-use: presenting it again is treated as theft...
		refresh(first, 401);
		// ...so even the newest token no longer works
		refresh(second, 401);
	}

	@Test
	void logoutRevokesTheRefreshToken() throws Exception {
		String email = uniqueEmail();
		register(email);
		String refreshToken = refreshTokenOf(login(email, PASSWORD));

		mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
				.andExpect(status().isNoContent());

		refresh(refreshToken, 401);
	}

	@Test
	void seededDemoAnalystHasTheAnalystRole() throws Exception {
		String accessToken = accessTokenOf(login("analyst@sentinelbank.dev", "Demo#12345"));

		mvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ANALYST"))
				.andExpect(jsonPath("$.kycStatus").value("VERIFIED"));
	}

	@Test
	void correlationIdFromTheCallerIsEchoedBack() throws Exception {
		MvcResult result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.header("X-Correlation-Id", "trace-me-123")
				.content(loginBody(uniqueEmail(), PASSWORD)))
				.andReturn();

		assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo("trace-me-123");
	}

	private void register(String email) throws Exception {
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email, PASSWORD, "Test User")))
				.andExpect(status().isCreated());
	}

	private String login(String email, String password) throws Exception {
		return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andReturn().getResponse().getContentAsString();
	}

	private String refresh(String refreshToken, int expectedStatus) throws Exception {
		return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
				.andExpect(status().is(expectedStatus))
				.andReturn().getResponse().getContentAsString();
	}

	private static String accessTokenOf(String tokenResponse) {
		return JsonPath.read(tokenResponse, "$.accessToken");
	}

	private static String refreshTokenOf(String tokenResponse) {
		return JsonPath.read(tokenResponse, "$.refreshToken");
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String registerBody(String email, String password, String fullName) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"" + fullName + "\"}";
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}
}
