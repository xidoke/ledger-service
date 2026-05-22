package com.xidoke.ledger.common.error;

/**
 * A requested resource does not exist. Mapped centrally to HTTP 404 (RFC 7807 ProblemDetail) by the web layer.
 * Framework-free on purpose so domain exceptions can extend it without importing Spring (hexagonal — ADR-0018).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
