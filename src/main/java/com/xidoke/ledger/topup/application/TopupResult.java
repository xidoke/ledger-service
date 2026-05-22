package com.xidoke.ledger.topup.application;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.common.domain.TransactionId;

/** Outcome of a top-up: the posting id and the credited account (with its updated balance). */
public record TopupResult(TransactionId transactionId, Account account) {}
