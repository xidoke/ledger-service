package com.xidoke.ledger.ledger;

import com.xidoke.ledger.common.domain.Direction;
import com.xidoke.ledger.common.domain.LedgerEntry;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root for a posting — the place where the double-entry invariant {@code Σ DEBIT == Σ CREDIT} is enforced
 * (ADR-0005). Entries accumulate while PENDING (Proposed-Object pattern); {@link #post()} validates the invariant and
 * freezes the transaction. Entries themselves are immutable facts owned by the shared kernel.
 */
public final class Transaction {

    private final TransactionId id;
    private final TransactionType type;
    private final List<LedgerEntry> entries = new ArrayList<>();
    private TransactionStatus status;

    public Transaction(TransactionId id, TransactionType type) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.status = TransactionStatus.PENDING;
    }

    /** Adds an entry to a still-PENDING transaction; the entry must reference this transaction. */
    public void addEntry(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (status != TransactionStatus.PENDING) {
            throw new TransactionAlreadyPostedException(id);
        }
        if (!entry.transactionId().equals(id)) {
            throw new IllegalArgumentException("entry belongs to a different transaction: " + entry.transactionId());
        }
        entries.add(entry);
    }

    /** Validates the double-entry invariant and freezes the transaction. */
    public void post() {
        if (status != TransactionStatus.PENDING) {
            throw new TransactionAlreadyPostedException(id);
        }
        if (entries.size() < 2) {
            throw new UnbalancedTransactionException(id, "a posting needs at least 2 entries, got " + entries.size());
        }
        Money net = Money.zero(entries.get(0).amount().currencyCode());
        for (LedgerEntry entry : entries) {
            net = entry.direction() == Direction.DEBIT ? net.subtract(entry.amount()) : net.add(entry.amount());
        }
        if (!net.isZero()) {
            throw new UnbalancedTransactionException(id, "Σ DEBIT must equal Σ CREDIT, net=" + net);
        }
        this.status = TransactionStatus.POSTED;
    }

    public TransactionId id() {
        return id;
    }

    public TransactionType type() {
        return type;
    }

    public TransactionStatus status() {
        return status;
    }

    public List<LedgerEntry> entries() {
        return Collections.unmodifiableList(entries);
    }
}
