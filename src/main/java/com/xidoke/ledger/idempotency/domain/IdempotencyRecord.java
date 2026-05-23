package com.xidoke.ledger.idempotency.domain;

import java.time.Instant;

/**
 * A stored idempotency outcome: the client-supplied {@code key}, the {@code requestHash} that produced it, and the
 * captured response (status + body) to replay on a retry with the same key. Immutable (ADR-0012).
 */
public record IdempotencyRecord(
        String key, String requestHash, int responseStatus, String responseBody, Instant createdAt) {}
