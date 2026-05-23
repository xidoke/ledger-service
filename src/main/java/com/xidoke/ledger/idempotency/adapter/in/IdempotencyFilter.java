package com.xidoke.ledger.idempotency.adapter.in;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.idempotency.domain.IdempotencyRecord;
import com.xidoke.ledger.idempotency.domain.IdempotencyStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Stripe-style idempotency for mutating requests (ADR-0012). When a {@code POST} carries an {@code Idempotency-Key}
 * header, the filter hashes the request (method + path + body) and consults the {@link IdempotencyStore}:
 *
 * <ul>
 *   <li><b>miss</b> → run the request, then store the response (only on 2xx) keyed by the header;
 *   <li><b>hit, same hash</b> → replay the stored response without re-running the side effect;
 *   <li><b>hit, different hash</b> → reject with 422 (the key was reused for a different request).
 * </ul>
 *
 * <p>The header is optional here (LDG-48); requiring it on {@code /transfers}+{@code /topups} and handling the
 * concurrent same-key in-flight race are LDG-49. Note: a filter runs <em>before</em> the DispatcherServlet, so an
 * exception thrown here would bypass {@code @RestControllerAdvice} — the 422 mismatch is therefore written here as
 * {@code application/problem+json} directly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // after CorrelationIdFilter, so replays still carry the correlation id
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "ObjectMapper is a shared, thread-safe Spring-managed singleton injected by the container;"
                    + " storing the reference is the intended Spring idiom, not exposure of mutable internal state.")
    public IdempotencyFilter(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (!"POST".equalsIgnoreCase(request.getMethod()) || key == null || key.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        String requestHash = hash(request.getMethod(), request.getRequestURI(), body);

        Optional<IdempotencyRecord> existing = store.find(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.requestHash().equals(requestHash)) {
                replay(response, record);
            } else {
                writeConflict(request, response, key);
            }
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request, body);
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        chain.doFilter(cachedRequest, cachingResponse);

        int status = cachingResponse.getStatus();
        String responseBody = new String(cachingResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
        // Only persist successful outcomes: a failed operation must stay retryable, not be locked behind the key.
        if (status >= 200 && status < 300) {
            store.save(new IdempotencyRecord(key, requestHash, status, responseBody, Instant.now()));
        }
        cachingResponse.copyBodyToResponse();
    }

    private void replay(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        response.setStatus(record.responseStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(record.responseBody());
    }

    private void writeConflict(HttpServletRequest request, HttpServletResponse response, String key)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Idempotency-Key '%s' was already used with a different request".formatted(key));
        problem.setTitle("Idempotency-Key conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private static String hash(String method, String uri, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((method + " " + uri + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
