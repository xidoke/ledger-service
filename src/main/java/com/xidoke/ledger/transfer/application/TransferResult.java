package com.xidoke.ledger.transfer.application;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.common.domain.TransactionId;

/** Outcome of a transfer: the posting id and both affected accounts (with their updated balances). */
public record TransferResult(TransactionId transactionId, Account from, Account to) {}
