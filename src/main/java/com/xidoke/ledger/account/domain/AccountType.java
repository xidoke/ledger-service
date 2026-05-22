package com.xidoke.ledger.account.domain;

/**
 * Distinguishes ordinary user wallets (credit-normal, may not overdraw) from system accounts such as SYSTEM_FUNDING
 * (allowed to hold a negative balance — it represents external funding, ADR-0009). The type drives the balance policy
 * in {@link Account#debit}.
 */
public enum AccountType {
    USER,
    SYSTEM
}
