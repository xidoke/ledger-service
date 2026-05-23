package com.xidoke.ledger.outbox.domain;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for the transactional outbox (ADR-0013). A use case calls {@link #append} inside its own
 * {@code @Transactional}, so the event row commits atomically with the ledger write — no dual-write. The poller drains
 * it via {@link #fetchPendingBatch} + {@link #markSent}.
 */
public interface OutboxRepository {

    /**
     * Append a PENDING event in the current transaction. {@code payload} is serialized to JSON by the adapter;
     * {@code aggregateId} is the entity the event is about (the transaction) and {@code schemaVersion} versions the
     * payload shape for future evolution.
     */
    void append(UUID aggregateId, String eventType, Object payload, int schemaVersion);

    /**
     * Lock and return up to {@code limit} PENDING events oldest-first ({@code FOR UPDATE SKIP LOCKED}), so concurrent
     * pollers take disjoint batches. The lock is held until the caller's transaction commits, so call inside the same
     * {@code @Transactional} as {@link #markSent}.
     */
    List<OutboxRecord> fetchPendingBatch(int limit);

    /** Mark an event SENT and stamp {@code published_at}. */
    void markSent(long id);
}
