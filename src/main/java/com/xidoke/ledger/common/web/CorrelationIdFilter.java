package com.xidoke.ledger.common.web;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns each request a correlation id — taken from the {@code X-Correlation-Id} header or a fresh UUID — puts it in
 * the SLF4J MDC so every log line of the request carries it, and echoes it back on the response. The MDC entry is
 * always cleared so pooled threads never leak it.
 *
 * <p>The inbound header is client-controlled, so it is validated against {@link #SAFE_CORRELATION_ID} before being
 * reflected into the response header or the MDC: anything containing CR/LF or control characters (or longer than 64
 * chars) is rejected in favour of a fresh UUID. This closes an HTTP response-splitting / log-injection vector while
 * still letting clients supply their own id (UUID, ULID, request-id, …).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    @SuppressFBWarnings(
            value = "HRS_REQUEST_PARAMETER_TO_HTTP_HEADER",
            justification = "The inbound header is validated against the SAFE_CORRELATION_ID allowlist"
                    + " ([A-Za-z0-9_-]{1,64}) before being reflected, so it cannot carry CR/LF or control"
                    + " characters — response-splitting is impossible. SpotBugs taint analysis does not model the"
                    + " regex guard; reviewed false positive, covered by CorrelationIdFilterTest.")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId =
                (header != null && SAFE_CORRELATION_ID.matcher(header).matches())
                        ? header
                        : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
