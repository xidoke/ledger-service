package com.xidoke.ledger.idempotency.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for the idempotency key lifecycle (ADR-0012). The flow is claim-first so concurrent same-key requests
 * are serialized at the database: {@link #claim} inserts a PENDING row iff the key is free, the winner runs the
 * operation and calls {@link #complete}, and a failed operation calls {@link #release} so the key stays retryable.
 */
public interface IdempotencyStore {

    /**
     * Atomically claim the key (INSERT … ON CONFLICT DO NOTHING). Returns {@code true} iff this caller won the claim.
     */
    boolean claim(String key, String requestHash);

    Optional<IdempotencyRecord> find(String key);

    /** Mark a claimed key COMPLETED and store the response to replay on future retries. */
    void complete(String key, int responseStatus, String responseBody);

    /** Drop a still-PENDING claim so the operation can be retried (e.g. it failed or threw). */
    void release(String key);

    /**
     * Delete expired rows so abandoned keys stop blocking retries: PENDING rows older than {@code pendingCutoff} (a
     * claim orphaned by a crash between {@link #claim} and {@link #complete}/{@link #release}) and COMPLETED rows older
     * than {@code completedCutoff} (past the client retry window). Returns the number of rows removed.
     */
    int sweepExpired(Instant pendingCutoff, Instant completedCutoff);
}
