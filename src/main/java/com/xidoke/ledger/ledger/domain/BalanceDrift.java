package com.xidoke.ledger.ledger.domain;

import java.util.UUID;

/**
 * A reconciliation finding (ADR-0016): an account whose cached {@code balance} no longer equals the signed sum of its
 * ledger entries. The cache should always equal the ledger (ADR-0006); a non-zero {@link #drift()} is silent corruption
 * to investigate (never auto-correct).
 */
public record BalanceDrift(UUID accountId, long cachedBalance, long ledgerBalance) {

    public long drift() {
        return cachedBalance - ledgerBalance;
    }
}
