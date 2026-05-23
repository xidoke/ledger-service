package com.xidoke.ledger.idempotency.domain;

import java.util.Optional;

/**
 * Outbound port for persisting + looking up idempotency outcomes (ADR-0012). The {@code key} is the primary key, so a
 * concurrent double-insert surfaces as a uniqueness violation — the in-flight race is handled in LDG-49.
 */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String key);

    void save(IdempotencyRecord record);
}
