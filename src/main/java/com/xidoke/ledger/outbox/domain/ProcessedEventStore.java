package com.xidoke.ledger.outbox.domain;

/**
 * The idempotent-consumer inbox (ADR-0013): an atomic dedup of event ids so an at-least-once redelivery is processed
 * once. Backed by a unique key + {@code INSERT … ON CONFLICT DO NOTHING}, so the claim is race-free (no check-then-act
 * window).
 */
public interface ProcessedEventStore {

    /** Atomically claim {@code eventId}. Returns {@code true} the first time, {@code false} if already processed. */
    boolean claim(long eventId);
}
