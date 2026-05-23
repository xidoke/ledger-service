package com.xidoke.ledger.idempotency.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A stored idempotency outcome (ADR-0012): the client-supplied {@code key}, the {@code requestHash} that claimed it,
 * its {@code status}, and — once {@code COMPLETED} — the captured response (status + body) to replay. While
 * {@code PENDING} the response fields are {@code null}.
 */
public record IdempotencyRecord(
        String key,
        String requestHash,
        IdempotencyStatus status,
        @Nullable Integer responseStatus,
        @Nullable String responseBody,
        Instant createdAt) {}
