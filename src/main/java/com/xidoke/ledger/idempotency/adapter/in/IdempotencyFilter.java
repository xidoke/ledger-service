package com.xidoke.ledger.idempotency.adapter.in;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.idempotency.domain.IdempotencyRecord;
import com.xidoke.ledger.idempotency.domain.IdempotencyStatus;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Stripe-style idempotency for mutating requests (ADR-0012), with concurrent in-flight handling. When a {@code POST}
 * carries an {@code Idempotency-Key}, the filter hashes the request (method + path + body, SHA-256) and claims the key
 * with an atomic {@code INSERT … ON CONFLICT DO NOTHING} (a PENDING row) before running it:
 *
 * <ul>
 *   <li><b>claim won</b> → run the request, then on 2xx store the response (COMPLETED); on a non-2xx (or a throw)
 *       release the key so the operation stays retryable;
 *   <li><b>claim lost, COMPLETED, same hash</b> → replay the stored response (side effect skipped);
 *   <li><b>claim lost, COMPLETED, different hash</b> → 422 (the key was reused for a different request);
 *   <li><b>claim lost, still PENDING</b> → 409 (a concurrent request with this key is still running).
 * </ul>
 *
 * <p>The header is <b>required</b> on the money endpoints ({@code /transfers}, {@code /accounts/*}/{@code topups}) — a
 * missing key there is 400. A filter runs before the DispatcherServlet, so its exceptions bypass
 * {@code @RestControllerAdvice}; the 400/409/422 responses are therefore written here as {@code problem+json} directly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // after CorrelationIdFilter, so replays still carry the correlation id
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final List<String> KEY_REQUIRED_PATTERNS = List.of("/transfers", "/accounts/*/topups");

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

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
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            if (requiresKey(request)) {
                writeProblem(
                        request,
                        response,
                        HttpStatus.BAD_REQUEST,
                        "Missing Idempotency-Key",
                        "An Idempotency-Key header is required for " + request.getRequestURI());
            } else {
                chain.doFilter(request, response);
            }
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        String requestHash = hash(request.getMethod(), request.getRequestURI(), body);

        if (store.claim(key, requestHash)) {
            runAndStore(request, response, chain, key, requestHash, body);
            return;
        }

        Optional<IdempotencyRecord> existing = store.find(key);
        if (existing.isEmpty() || existing.get().status() == IdempotencyStatus.PENDING) {
            // PENDING (or claimed-then-released by a racing request): a same-key request is still in flight.
            writeProblem(
                    request,
                    response,
                    HttpStatus.CONFLICT,
                    "Idempotency-Key in flight",
                    "A request with Idempotency-Key '%s' is still being processed".formatted(key));
            return;
        }

        IdempotencyRecord record = existing.get();
        if (record.requestHash().equals(requestHash)) {
            replay(response, record);
        } else {
            writeProblem(
                    request,
                    response,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key conflict",
                    "Idempotency-Key '%s' was already used with a different request".formatted(key));
        }
    }

    private void runAndStore(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            String key,
            String requestHash,
            byte[] body)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request, body);
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        try {
            chain.doFilter(cachedRequest, cachingResponse);
            int status = cachingResponse.getStatus();
            String responseBody = new String(cachingResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            // Only persist successful outcomes: a failed operation must stay retryable, not be locked behind the key.
            if (status >= 200 && status < 300) {
                store.complete(key, status, responseBody);
                completed = true;
            }
            cachingResponse.copyBodyToResponse();
        } finally {
            if (!completed) {
                store.release(key); // failed or threw → drop the PENDING claim so the client can retry
            }
        }
    }

    private boolean requiresKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return KEY_REQUIRED_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    private void replay(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        Integer status = record.responseStatus();
        String body = record.responseBody();
        response.setStatus(status != null ? status : HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body != null ? body : "");
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatusCode status,
            String title,
            String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
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
