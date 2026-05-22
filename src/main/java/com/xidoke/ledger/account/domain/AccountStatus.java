package com.xidoke.ledger.account.domain;

/** Lifecycle state of an {@link Account}. Only an ACTIVE account accepts debits/credits. */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
