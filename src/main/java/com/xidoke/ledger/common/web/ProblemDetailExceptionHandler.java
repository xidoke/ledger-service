package com.xidoke.ledger.common.web;

import com.xidoke.ledger.common.error.ConcurrencyConflictException;
import com.xidoke.ledger.common.error.NotFoundException;
import com.xidoke.ledger.common.error.UnprocessableEntityException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to RFC 7807 ProblemDetail responses (application/problem+json) so every error shares one
 * machine-readable shape.
 *
 * <p>Status taxonomy for domain failures (kept deliberately distinct so each code carries one meaning):
 *
 * <ul>
 *   <li>{@code 400} — malformed/invalid request syntax (bean-validation failures);
 *   <li>{@code 404} — {@link NotFoundException}: the target resource does not exist;
 *   <li>{@code 422} — {@link UnprocessableEntityException}: well-formed request, existing target, but a business rule
 *       forbids it (insufficient funds, self-transfer, currency mismatch);
 *   <li>{@code 409} — a conflict with current state: {@link IllegalStateException}, or a concurrency conflict
 *       ({@link ConcurrencyConflictException} from exhausted optimistic-lock retry, ADR-0011; or a raw
 *       {@link OptimisticLockingFailureException} that escaped retry).
 * </ul>
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setTitle("Validation failed");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String message = error.getDefaultMessage();
            fieldErrors.put(error.getField(), message == null ? "invalid" : message);
        });
        body.setProperty("fieldErrors", fieldErrors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
        body.setTitle("Not Found");
        return handleExceptionInternal(ex, body, headers, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    ProblemDetail handleUnprocessable(UnprocessableEntityException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Unprocessable Entity");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConflict(IllegalStateException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler({ConcurrencyConflictException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleConcurrencyConflict(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicted with a concurrent update; please retry");
        problem.setTitle("Concurrency conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
