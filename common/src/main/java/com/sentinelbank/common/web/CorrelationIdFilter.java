package com.sentinelbank.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the correlation ID from the incoming request (created by the gateway), or creates one if
 * missing, puts it in the logging MDC and echoes it on the response.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = request.getHeader(CorrelationIds.HEADER);
		if (id == null || id.isBlank()) {
			id = CorrelationIds.newId();
		}
		MDC.put(CorrelationIds.MDC_KEY, id);
		response.setHeader(CorrelationIds.HEADER, id);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(CorrelationIds.MDC_KEY);
		}
	}
}
