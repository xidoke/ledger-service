package com.xidoke.ledger.ledger.domain;

import java.util.List;

/**
 * Read-only port for reconciling the balance cache against the append-only ledger (ADR-0016). Both checks re-derive
 * from {@code ledger_entries}, which is always possible because entries are immutable (ADR-0005).
 */
public interface ReconciliationRepository {

    /**
     * Accounts whose cached {@code balance} differs from the signed sum of their entries (CREDIT {@code +}, DEBIT
     * {@code -}). Returns only the mismatches — empty means the cache is consistent.
     */
    List<BalanceDrift> findBalanceDrift();

    /**
     * System-wide trial balance: {@code Σ DEBIT − Σ CREDIT} over all entries, which must be {@code 0}. A non-zero
     * result means the double-entry invariant is broken — money was created or destroyed.
     */
    long trialBalanceImbalance();
}
