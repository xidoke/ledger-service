package com.xidoke.ledger.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    private static final TransactionId TX = TransactionId.newId();
    private static final AccountId ACC = AccountId.newId();
    private static final Instant AT = Instant.parse("2026-05-22T00:00:00Z");

    @Test
    void carriesAllComponents() {
        LedgerEntry entry = new LedgerEntry(TX, ACC, Direction.DEBIT, Money.of(100, "USD"), AT);

        assertThat(entry.transactionId()).isEqualTo(TX);
        assertThat(entry.accountId()).isEqualTo(ACC);
        assertThat(entry.direction()).isEqualTo(Direction.DEBIT);
        assertThat(entry.amount()).isEqualTo(Money.of(100, "USD"));
        assertThat(entry.createdAt()).isEqualTo(AT);
    }

    @Test
    void amountMustBePositive() {
        assertThatThrownBy(() -> new LedgerEntry(TX, ACC, Direction.DEBIT, Money.of(0, "USD"), AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LedgerEntry(TX, ACC, Direction.CREDIT, Money.of(-1, "USD"), AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullComponentsRejected() {
        assertThatThrownBy(() -> new LedgerEntry(null, ACC, Direction.DEBIT, Money.of(1, "USD"), AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LedgerEntry(TX, ACC, Direction.DEBIT, Money.of(1, "USD"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void valueEqualityByComponents() {
        LedgerEntry a = new LedgerEntry(TX, ACC, Direction.DEBIT, Money.of(100, "USD"), AT);
        LedgerEntry b = new LedgerEntry(TX, ACC, Direction.DEBIT, Money.of(100, "USD"), AT);

        assertThat(a).isEqualTo(b);
    }
}
