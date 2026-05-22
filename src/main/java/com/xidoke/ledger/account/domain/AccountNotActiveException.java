package com.xidoke.ledger.account.domain;

import com.xidoke.ledger.common.domain.AccountId;

/** Thrown when a debit/credit is attempted on an account that is not ACTIVE. */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(AccountId accountId, AccountStatus status) {
        super("Account %s is not active: status=%s".formatted(accountId, status));
    }
}
