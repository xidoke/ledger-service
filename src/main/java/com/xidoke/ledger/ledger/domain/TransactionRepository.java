package com.xidoke.ledger.ledger.domain;

/**
 * Persists a posted {@link Transaction} — its header plus all its (append-only) ledger entries — as one unit. The
 * caller is responsible for the surrounding {@code @Transactional} so the entries, the transaction row, and the
 * affected account balances all commit together (ADR-0006).
 */
public interface TransactionRepository {

    void save(Transaction transaction);
}
