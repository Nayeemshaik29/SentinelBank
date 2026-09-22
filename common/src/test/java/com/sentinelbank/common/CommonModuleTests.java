package com.sentinelbank.common;

import java.util.UUID;

import com.sentinelbank.common.error.ApiException;
import com.sentinelbank.common.error.GlobalExceptionHandler;
import com.sentinelbank.common.event.EventEnvelope;
import com.sentinelbank.common.event.Topics;
import com.sentinelbank.common.web.CorrelationIdFilter;
import com.sentinelbank.common.web.CorrelationIds;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

class CommonModuleTests {

	@Test
	void filterKeepsIncomingCorrelationIdAndEchoesItOnTheResponse() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CorrelationIds.HEADER, "abc-123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		String[] seenInsideChain = new String[1];
		FilterChain chain = (req, res) -> seenInsideChain[0] = MDC.get(CorrelationIds.MDC_KEY);

		new CorrelationIdFilter().doFilter(request, response, chain);

		assertThat(seenInsideChain[0]).isEqualTo("abc-123");
		assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("abc-123");
		assertThat(MDC.get(CorrelationIds.MDC_KEY)).as("MDC is cleaned up after the request").isNull();
	}

	@Test
	void filterCreatesACorrelationIdWhenNoneIsSent() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new CorrelationIdFilter().doFilter(new MockHttpServletRequest(), response, (req, res) -> { });

		assertThat(UUID.fromString(response.getHeader(CorrelationIds.HEADER))).isNotNull();
	}

	@Test
	void apiExceptionBecomesAProblemDetailWithTheBusinessCode() {
		ProblemDetail problem = new GlobalExceptionHandler()
				.handleApi(new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Not enough balance"));

		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getDetail()).isEqualTo("Not enough balance");
		assertThat(problem.getProperties()).containsEntry("code", "INSUFFICIENT_FUNDS");
	}

	@Test
	void eventEnvelopeGetsAUniqueIdPerEvent() {
		EventEnvelope<String> first = EventEnvelope.of(Topics.TRANSFER_INITIATED, "acc-1", "corr-1", "payload");
		EventEnvelope<String> second = EventEnvelope.of(Topics.TRANSFER_INITIATED, "acc-1", "corr-1", "payload");

		assertThat(first.eventId()).isNotEqualTo(second.eventId());
		assertThat(first.type()).isEqualTo("transfer.initiated");
		assertThat(first.aggregateId()).isEqualTo("acc-1");
		assertThat(first.occurredAt()).isNotNull();
	}

	@Test
	void topicHelpersBuildRetryAndDeadLetterNames() {
		assertThat(Topics.retry(Topics.TRANSFER_FAILED)).isEqualTo("transfer.failed.retry");
		assertThat(Topics.dlt(Topics.TRANSFER_FAILED)).isEqualTo("transfer.failed.dlt");
	}

	@Test
	void baseStripsARetryOrDltSuffixSoADltDestinationIsNeverComputedTwice() {
		// This is exactly the bug it exists to prevent: a retry-tier consumer computing .dlt directly from
		// record.topic() (already "...retry") would produce "...retry.dlt" instead of "....dlt".
		assertThat(Topics.base(Topics.retry(Topics.TRANSFER_INITIATED))).isEqualTo(Topics.TRANSFER_INITIATED);
		assertThat(Topics.base(Topics.dlt(Topics.TRANSFER_INITIATED))).isEqualTo(Topics.TRANSFER_INITIATED);
		assertThat(Topics.base(Topics.TRANSFER_INITIATED)).isEqualTo(Topics.TRANSFER_INITIATED);
		assertThat(Topics.dlt(Topics.base(Topics.retry(Topics.TRANSFER_INITIATED))))
				.isEqualTo("transfer.initiated.dlt");
	}

	@Test
	void missingRequiredHeaderBecomesAClearValidationProblem() throws NoSuchMethodException {
		var parameter = new org.springframework.core.MethodParameter(
				HandlerMethod.class.getDeclaredMethod("hashCode"), -1);
		MissingRequestHeaderException ex = new MissingRequestHeaderException("Idempotency-Key", parameter);

		ProblemDetail problem = new GlobalExceptionHandler().handleMissingHeader(ex);

		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getDetail()).contains("Idempotency-Key");
		assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_FAILED");
	}
}
