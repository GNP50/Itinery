package com.travel.messaging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet {@link Filter} that propagates distributed tracing correlation IDs
 * across service boundaries using the de-facto {@code X-Correlation-Id} HTTP
 * header convention.
 *
 * <h3>Inbound behaviour</h3>
 * <ol>
 *   <li>Reads the {@code X-Correlation-Id} request header.</li>
 *   <li>If the header is present and non-blank its value is used; otherwise a
 *       new random {@link UUID} is generated so that every request has a
 *       correlation ID regardless of whether the upstream caller forwarded one.</li>
 *   <li>The ID is stored in the SLF4J {@link MDC} under the key
 *       {@value #MDC_KEY} so that log appenders can include it in every
 *       log line automatically (configure {@code %X{correlationId}} in your
 *       Logback/Log4j2 pattern).</li>
 * </ol>
 *
 * <h3>Outbound behaviour</h3>
 * <p>The correlation ID is always echoed back to the caller via the response
 * {@code X-Correlation-Id} header so that clients can correlate their
 * log entries with the server-side trace.
 *
 * <h3>MDC cleanup</h3>
 * <p>The MDC entry is removed in a {@code finally} block after the filter
 * chain completes, preventing ID leakage across requests on a thread-pooled
 * server.
 *
 * <h3>Ordering</h3>
 * <p>The filter is registered at {@link Order#HIGHEST_PRECEDENCE} + 1 so it
 * runs as early as possible in the chain but still after security filters.
 * Adjust the order constant if your security filter chain requires a different
 * position.
 */
@Component
@Order(Integer.MIN_VALUE + 1)  // Run very early – just after Spring Security
public class CorrelationIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    /** HTTP request/response header name for the correlation identifier. */
    public static final String HEADER_NAME = "X-Correlation-Id";

    /** SLF4J MDC key under which the correlation ID is stored. */
    public static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            // Non-HTTP requests (unlikely in a Spring MVC context) – pass through.
            chain.doFilter(request, response);
            return;
        }

        String correlationId = resolveCorrelationId(httpRequest);

        MDC.put(MDC_KEY, correlationId);
        // Echo the ID in the response so upstream callers can correlate.
        httpResponse.setHeader(HEADER_NAME, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Always clean up MDC to prevent leakage on thread pools.
            MDC.remove(MDC_KEY);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Extracts the correlation ID from the inbound request header, or generates
     * a fresh UUID if the header is absent or blank.
     *
     * @param request the incoming HTTP request
     * @return a non-null, non-blank correlation ID string
     */
    private String resolveCorrelationId(HttpServletRequest request) {
        String fromHeader = request.getHeader(HEADER_NAME);
        if (fromHeader != null && !fromHeader.isBlank()) {
            log.trace("Using inbound correlation ID: {}", fromHeader);
            return fromHeader.trim();
        }
        String generated = UUID.randomUUID().toString();
        log.trace("Generated new correlation ID: {}", generated);
        return generated;
    }
}
