package com.xidoke.ledger.ledger;

import com.xidoke.ledger.common.domain.TransactionId;

/** Thrown when modifying or re-posting a transaction that is already POSTED. */
public class TransactionAlreadyPostedException extends RuntimeException {

    public TransactionAlreadyPostedException(TransactionId transactionId) {
        super("Transaction %s is already posted and cannot be modified".formatted(transactionId));
    }
}
