package com.xidoke.ledger.transfer.application;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.account.domain.AccountNotFoundException;
import com.xidoke.ledger.account.domain.AccountRepository;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import com.xidoke.ledger.ledger.domain.Transaction;
import com.xidoke.ledger.ledger.domain.TransactionRepository;
import com.xidoke.ledger.ledger.domain.TransactionType;
import com.xidoke.ledger.outbox.domain.OutboxRepository;
import com.xidoke.ledger.transfer.domain.SameCurrencyRequiredException;
import com.xidoke.ledger.transfer.domain.SelfTransferException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transfer use case: move money between two user accounts as one balanced posting — {@code DEBIT from / CREDIT to}
 * (ADR-0005). Both balance caches and the transaction + its entries commit in a single {@code @Transactional}
 * (ADR-0006), so any rejection rolls the whole posting back. The source account's no-overdraw invariant is enforced by
 * {@link Account#debit}, which throws before any write. The {@code TransferPosted} outbox event is appended in the same
 * transaction (ADR-0013), so it commits with the posting or not at all. Idempotency and concurrency retry are applied
 * by the outer layers (filter + controller), not here.
 */
@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final OutboxRepository outbox;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Repositories are stateless Spring-managed singletons injected by the container")
    public TransferService(AccountRepository accounts, TransactionRepository transactions, OutboxRepository outbox) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.outbox = outbox;
    }

    @Transactional
    public TransferResult transfer(AccountId fromId, AccountId toId, long amountMinorUnits) {
        if (fromId.equals(toId)) {
            throw new SelfTransferException(fromId);
        }
        Account from = accounts.findById(fromId).orElseThrow(() -> new AccountNotFoundException(fromId));
        Account to = accounts.findById(toId).orElseThrow(() -> new AccountNotFoundException(toId));
        if (!from.currencyCode().equals(to.currencyCode())) {
            throw new SameCurrencyRequiredException(from.currencyCode(), to.currencyCode());
        }

        Money amount = Money.of(amountMinorUnits, from.currencyCode());
        Instant now = Instant.now();
        TransactionId txId = TransactionId.newId();

        Transaction tx = new Transaction(txId, TransactionType.TRANSFER);
        tx.addEntry(from.debit(amount, txId, now)); // throws InsufficientFundsException if balance < amount
        tx.addEntry(to.credit(amount, txId, now));
        tx.post(); // enforces Σ DEBIT == Σ CREDIT

        accounts.save(from);
        accounts.save(to);
        transactions.save(tx);
        outbox.append(
                txId.value(),
                "TransferPosted",
                new TransferPosted(
                        txId.value(), fromId.value(), toId.value(), amount.minorUnits(), from.currencyCode(), now),
                1);

        return new TransferResult(txId, from, to);
    }
}
