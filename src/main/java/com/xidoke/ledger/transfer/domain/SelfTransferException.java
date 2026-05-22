package com.xidoke.ledger.transfer.domain;

import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.error.UnprocessableEntityException;

/** Thrown when a transfer names the same account as both source and destination — a no-op the ledger rejects. */
public class SelfTransferException extends UnprocessableEntityException {

    public SelfTransferException(AccountId accountId) {
        super("Cannot transfer to the same account: " + accountId);
    }
}
