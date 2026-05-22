package com.xidoke.ledger.ledger.domain;

/** Lifecycle of a {@link Transaction}: entries accumulate while PENDING, then become immutable once POSTED. */
public enum TransactionStatus {
    PENDING,
    POSTED
}
