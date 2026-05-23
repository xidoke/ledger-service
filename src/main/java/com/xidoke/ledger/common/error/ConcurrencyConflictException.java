package com.xidoke.ledger.common.error;

/**
 * A write could not be applied because a concurrent writer kept winning the optimistic-lock race, and the bounded retry
 * was exhausted. Mapped centrally to HTTP 409 (RFC 7807 ProblemDetail) — a genuine concurrency conflict the client may
 * retry later. Framework-free so it can be thrown without importing Spring (hexagonal — ADR-0018); kept distinct from
 * 422 (a business-rule rejection of a well-formed request).
 */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
