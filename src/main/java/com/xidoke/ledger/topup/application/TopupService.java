package com.xidoke.ledger.topup.application;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.account.domain.AccountNotFoundException;
import com.xidoke.ledger.account.domain.AccountRepository;
import com.xidoke.ledger.account.domain.SystemAccounts;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import com.xidoke.ledger.ledger.domain.Transaction;
import com.xidoke.ledger.ledger.domain.TransactionRepository;
import com.xidoke.ledger.ledger.domain.TransactionType;
import com.xidoke.ledger.outbox.domain.OutboxRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Top-up use case: move money from outside into a user wallet as one balanced posting — {@code DEBIT SYSTEM_FUNDING /
 * CREDIT user} (ADR-0009). All writes (both account balances + the transaction + its entries) commit in a single
 * {@code @Transactional} (ADR-0006), and the {@code TopupPosted} outbox event is appended in that same transaction
 * (ADR-0013). Idempotency and concurrency retry are applied by the outer layers (filter + controller), not here.
 */
@Service
public class TopupService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final OutboxRepository outbox;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Repositories are stateless Spring-managed singletons injected by the container")
    public TopupService(AccountRepository accounts, TransactionRepository transactions, OutboxRepository outbox) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.outbox = outbox;
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
        outbox.append(
                txId.value(),
                "TopupPosted",
                new TopupPosted(txId.value(), accountId.value(), amount.minorUnits(), user.currencyCode(), now),
                1);

        return new TopupResult(txId, user);
    }
}
