package com.xidoke.ledger.topup.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox event payload for a completed top-up (ADR-0013). Plain scalars (not domain value objects) so the JSON shape
 * stays a stable contract for downstream consumers, independent of internal types.
 */
public record TopupPosted(
        UUID transactionId, UUID accountId, long amountMinorUnits, String currency, Instant occurredAt) {}
