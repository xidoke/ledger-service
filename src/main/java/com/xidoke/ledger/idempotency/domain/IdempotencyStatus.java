package com.xidoke.ledger.idempotency.domain;

/**
 * Lifecycle of an idempotency key: {@code PENDING} once a request has claimed it but not yet finished,
 * {@code COMPLETED} once the response is stored. A second request seeing {@code PENDING} is an in-flight conflict
 * (409); seeing {@code COMPLETED} replays the stored response (ADR-0012).
 */
public enum IdempotencyStatus {
    PENDING,
    COMPLETED
}
