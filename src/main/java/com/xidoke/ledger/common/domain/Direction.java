package com.xidoke.ledger.common.domain;

/** The side of a {@link LedgerEntry}. For a credit-normal wallet, CREDIT increases balance, DEBIT decreases it. */
public enum Direction {
    DEBIT,
    CREDIT
}
