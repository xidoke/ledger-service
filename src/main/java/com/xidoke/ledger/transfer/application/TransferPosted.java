package com.xidoke.ledger.transfer.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox event payload for a completed transfer (ADR-0013). Plain scalars (not domain value objects) so the JSON shape
 * stays a stable contract for downstream consumers, independent of internal types.
 */
public record TransferPosted(
        UUID transactionId,
        UUID fromAccountId,
        UUID toAccountId,
        long amountMinorUnits,
        String currency,
        Instant occurredAt) {}
