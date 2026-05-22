package com.xidoke.ledger.account;

import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.CurrencyMismatchException;
import com.xidoke.ledger.common.domain.Direction;
import com.xidoke.ledger.common.domain.LedgerEntry;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Aggregate root and locking boundary (ADR-0010). Owns its cached {@code balance} and {@code version};
 * {@code debit}/{@code credit} are the only ways to move the balance and they enforce the per-account invariants
 * (account must be ACTIVE; debit may not overdraw). Each operation emits an immutable {@link LedgerEntry} fact
 * describing what happened (ADR-0005 log-is-truth). {@code ownerRef} is null for system accounts such as SYSTEM_FUNDING
 * (ADR-0010).
 */
public class Account {

    private final AccountId id;
    private final @Nullable String ownerRef;
    private final String currencyCode;
    private AccountStatus status;
    private Money balance;
    private long version;

    public Account(
            AccountId id,
            @Nullable String ownerRef,
            String currencyCode,
            AccountStatus status,
            Money balance,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerRef = ownerRef;
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        this.status = Objects.requireNonNull(status, "status");
        this.balance = Objects.requireNonNull(balance, "balance");
        if (!balance.currencyCode().equals(currencyCode)) {
            throw new CurrencyMismatchException(currencyCode, balance.currencyCode());
        }
        this.version = version;
    }

    /** Opens a fresh ACTIVE account with a zero balance. */
    public static Account open(AccountId id, @Nullable String ownerRef, String currencyCode) {
        return new Account(id, ownerRef, currencyCode, AccountStatus.ACTIVE, Money.zero(currencyCode), 0L);
    }

    /** Decreases the balance and emits a DEBIT entry. Rejects an overdraw. */
    public LedgerEntry debit(Money amount, TransactionId transactionId, Instant occurredAt) {
        requireActive();
        requirePositive(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        this.balance = balance.subtract(amount);
        return new LedgerEntry(transactionId, id, Direction.DEBIT, amount, occurredAt);
    }

    /** Increases the balance and emits a CREDIT entry. */
    public LedgerEntry credit(Money amount, TransactionId transactionId, Instant occurredAt) {
        requireActive();
        requirePositive(amount);
        this.balance = balance.add(amount);
        return new LedgerEntry(transactionId, id, Direction.CREDIT, amount, occurredAt);
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(id, status);
        }
    }

    private void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }

    public AccountId id() {
        return id;
    }

    public @Nullable String ownerRef() {
        return ownerRef;
    }

    public String currencyCode() {
        return currencyCode;
    }

    public AccountStatus status() {
        return status;
    }

    public Money balance() {
        return balance;
    }

    public long version() {
        return version;
    }
}
