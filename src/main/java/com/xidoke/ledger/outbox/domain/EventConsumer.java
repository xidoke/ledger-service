package com.xidoke.ledger.outbox.domain;

/**
 * Receives an event drained from the outbox (ADR-0013). Delivery is at-least-once (the poller may redeliver after a
 * crash between publish and mark-sent), so implementations must be idempotent — process each event's side effect at
 * most once. The Nấc-0 implementation logs; Phase 2 swaps in a real broker consumer.
 */
public interface EventConsumer {

    void handle(OutboxRecord event);
}
