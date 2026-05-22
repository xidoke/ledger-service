package com.xidoke.ledger.account.domain;

import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Money;

/** Thrown when a debit would drive an account's balance negative. */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(AccountId accountId, Money balance, Money requested) {
        super("Insufficient funds in account %s: balance=%s, requested=%s".formatted(accountId, balance, requested));
    }
}
