package com.xidoke.ledger.topup.application;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.account.domain.AccountNotFoundException;
import com.xidoke.ledger.account.domain.AccountRepository;
import com.xidoke.ledger.account.domain.SystemAccounts;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import com.xidoke.ledger.ledger.Transaction;
import com.xidoke.ledger.ledger.TransactionRepository;
import com.xidoke.ledger.ledger.TransactionType;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Top-up use case: move money from outside into a user wallet as one balanced posting — {@code DEBIT SYSTEM_FUNDING /
 * CREDIT user} (ADR-0009). All writes (both account balances + the transaction + its entries) commit in a single
 * {@code @Transactional} (ADR-0006). No idempotency (M3) or concurrency retry (M4) yet.
 */
@Service
public class TopupService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public TopupService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public TopupResult topup(AccountId accountId, long amountMinorUnits) {
        Account user = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        Account funding = accounts.findById(SystemAccounts.SYSTEM_FUNDING_ID)
                .orElseThrow(() -> new IllegalStateException("SYSTEM_FUNDING account is not seeded"));

        Money amount = Money.of(amountMinorUnits, user.currencyCode());
        Instant now = Instant.now();
        TransactionId txId = TransactionId.newId();

        Transaction tx = new Transaction(txId, TransactionType.TOPUP);
        tx.addEntry(funding.debit(amount, txId, now)); // SYSTEM_FUNDING is allowed to go negative
        tx.addEntry(user.credit(amount, txId, now));
        tx.post(); // enforces Σ DEBIT == Σ CREDIT

        accounts.save(funding);
        accounts.save(user);
        transactions.save(tx);

        return new TopupResult(txId, user);
    }
}
