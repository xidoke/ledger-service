package com.xidoke.ledger.ledger;

import com.xidoke.ledger.common.domain.TransactionId;

/** Thrown when a transaction's entries do not satisfy {@code Σ DEBIT == Σ CREDIT}. */
public class UnbalancedTransactionException extends RuntimeException {

    public UnbalancedTransactionException(TransactionId transactionId, String detail) {
        super("Unbalanced transaction %s: %s".formatted(transactionId, detail));
    }
}
