package com.xidoke.ledger.common.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable accounting fact: account {@code accountId} was moved by {@code amount} on side {@code direction} as part
 * of transaction {@code transactionId} at {@code createdAt}. Append-only — once created it never changes; corrections
 * are new reversing entries (ADR-0005). {@code amount} is always positive; {@code direction} carries the sign.
 */
public record LedgerEntry(
        TransactionId transactionId, AccountId accountId, Direction direction, Money amount, Instant createdAt) {

    public LedgerEntry {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("LedgerEntry amount must be positive: " + amount);
        }
    }
}
