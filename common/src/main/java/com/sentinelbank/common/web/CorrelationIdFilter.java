package com.sentinelbank.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the correlation ID from the incoming request (created by the gateway), or creates one if
 * missing, puts it in the logging MDC and echoes it on the response.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = request.getHeader(CorrelationIds.HEADER);
		if (id == null || id.isBlank()) {
			id = CorrelationIds.newId();
		}
		MDC.put(CorrelationIds.MDC_KEY, id);
		response.setHeader(CorrelationIds.HEADER, id);
		long started = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			if (!request.getRequestURI().startsWith("/actuator/")) {
				log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
						(System.nanoTime() - started) / 1_000_000);
			}
			MDC.remove(CorrelationIds.MDC_KEY);
		}
	}
}
