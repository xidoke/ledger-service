package com.xidoke.ledger.common.error;

/**
 * A request is well-formed and its target exists, but a business rule forbids processing it — e.g. a debit that would
 * overdraw an account, or a transfer between accounts of different currencies. Mapped centrally to HTTP 422 (RFC 7807
 * ProblemDetail) by the web layer.
 *
 * <p>Framework-free on purpose so domain exceptions can extend it without importing Spring (hexagonal — ADR-0018),
 * mirroring {@link NotFoundException} (→ 404). Kept distinct from 409 Conflict, which is reserved for concurrency
 * conflicts (optimistic-lock retry exhaustion, M4), and from 400, which is for malformed/invalid request syntax.
 */
public class UnprocessableEntityException extends RuntimeException {

    public UnprocessableEntityException(String message) {
        super(message);
    }
}
