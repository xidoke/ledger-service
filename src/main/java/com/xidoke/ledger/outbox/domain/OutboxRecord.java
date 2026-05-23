package com.xidoke.ledger.outbox.domain;

import java.util.UUID;

/**
 * A row read back from the outbox for delivery (ADR-0013). {@code payload} is the raw JSON string as stored — the Nấc-0
 * poller only logs it; a real consumer would deserialize by {@code eventType} + {@code schemaVersion}.
 */
public record OutboxRecord(long id, UUID aggregateId, String eventType, String payload, int schemaVersion) {}
