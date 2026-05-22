package com.xidoke.ledger.account.domain;

import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.error.UnprocessableEntityException;

/** Thrown when a debit would drive an account's balance negative. Mapped to HTTP 422 (a business-rule rejection). */
public class InsufficientFundsException extends UnprocessableEntityException {

    public InsufficientFundsException(AccountId accountId, Money balance, Money requested) {
        super("Insufficient funds in account %s: balance=%s, requested=%s".formatted(accountId, balance, requested));
    }
}
