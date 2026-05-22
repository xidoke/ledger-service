package com.xidoke.ledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Direction;
import com.xidoke.ledger.common.domain.LedgerEntry;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final Instant AT = Instant.parse("2026-05-22T00:00:00Z");
    private static final TransactionId TX = TransactionId.newId();
    private static final AccountId FROM = AccountId.newId();
    private static final AccountId TO = AccountId.newId();

    private static LedgerEntry entry(AccountId account, Direction direction, long amount) {
        return new LedgerEntry(TX, account, direction, Money.of(amount, "USD"), AT);
    }

    @Test
    void newTransactionIsPending() {
        Transaction tx = new Transaction(TX, TransactionType.TRANSFER);

        assertThat(tx.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(tx.entries()).isEmpty();
    }

    @Test
    void balancedPostingBecomesPosted() {
        Transaction tx = new Transaction(TX, TransactionType.TRANSFER);
        tx.addEntry(entry(FROM, Direction.DEBIT, 100));
        tx.addEntry(entry(TO, Direction.CREDIT, 100));

        tx.post();

        assertThat(tx.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(tx.entries()).hasSize(2);
    }

    @Test
    void unbalancedPostingIsRejected() {
        Transaction tx = new Transaction(TX, TransactionType.TRANSFER);
        tx.addEntry(entry(FROM, Direction.DEBIT, 100));
        tx.addEntry(entry(TO, Direction.CREDIT, 90));

        assertThatExceptionOfType(UnbalancedTransactionException.class).isThrownBy(tx::post);
        assertThat(tx.status()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void postingNeedsAtLeastTwoEntries() {
        Transaction tx = new Transaction(TX, TransactionType.TOPUP);
        tx.addEntry(entry(FROM, Direction.DEBIT, 100));

        assertThatExceptionOfType(UnbalancedTransactionException.class).isThrownBy(tx::post);
    }

    @Test
    void cannotAddEntryAfterPost() {
        Transaction tx = new Transaction(TX, TransactionType.TRANSFER);
        tx.addEntry(entry(FROM, Direction.DEBIT, 100));
        tx.addEntry(entry(TO, Direction.CREDIT, 100));
        tx.post();

        assertThatExceptionOfType(TransactionAlreadyPostedException.class)
                .isThrownBy(() -> tx.addEntry(entry(TO, Direction.CREDIT, 1)));
    }

    @Test
    void cannotPostTwice() {
        Transaction tx = new Transaction(TX, TransactionType.TRANSFER);
        tx.addEntry(entry(FROM, Direction.DEBIT, 100));
        tx.addEntry(entry(TO, Direction.CREDIT, 100));
        tx.post();

        assertThatExceptionOfType(TransactionAlreadyPostedException.class).isThrownBy(tx::post);
    }

    @Test
    void entryMustReferenceThisTransaction() {
        Transaction tx = new Transaction(TX, TransactionType.TRANSFER);
        LedgerEntry foreign = new LedgerEntry(TransactionId.newId(), FROM, Direction.DEBIT, Money.of(100, "USD"), AT);

        assertThatThrownBy(() -> tx.addEntry(foreign)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entriesViewIsUnmodifiable() {
        Transaction tx = new Transaction(TX, TransactionType.TRANSFER);
        tx.addEntry(entry(FROM, Direction.DEBIT, 100));

        assertThatThrownBy(() -> tx.entries().add(entry(TO, Direction.CREDIT, 100)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
