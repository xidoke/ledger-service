package com.xidoke.ledger.account.domain;

import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.error.NotFoundException;

/** Thrown when an account id does not resolve to a stored account. Mapped to HTTP 404. */
public class AccountNotFoundException extends NotFoundException {

    public AccountNotFoundException(AccountId id) {
        super("Account not found: " + id.value());
    }
}
