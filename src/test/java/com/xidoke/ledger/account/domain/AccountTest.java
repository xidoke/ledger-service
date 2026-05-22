package com.xidoke.ledger.account.domain;

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

class AccountTest {

    private static final Instant AT = Instant.parse("2026-05-22T00:00:00Z");
    private static final TransactionId TX = TransactionId.newId();

    @Test
    void openStartsActiveWithZeroBalance() {
        Account account = Account.open(AccountId.newId(), "owner-1", "USD");

        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.balance()).isEqualTo(Money.zero("USD"));
        assertThat(account.version()).isZero();
    }

    @Test
    void creditIncreasesBalanceAndEmitsCreditEntry() {
        Account account = Account.open(AccountId.newId(), "owner-1", "USD");

        LedgerEntry entry = account.credit(Money.of(500, "USD"), TX, AT);

        assertThat(account.balance()).isEqualTo(Money.of(500, "USD"));
        assertThat(entry.direction()).isEqualTo(Direction.CREDIT);
        assertThat(entry.accountId()).isEqualTo(account.id());
        assertThat(entry.transactionId()).isEqualTo(TX);
        assertThat(entry.amount()).isEqualTo(Money.of(500, "USD"));
        assertThat(entry.createdAt()).isEqualTo(AT);
    }

    @Test
    void debitDecreasesBalanceAndEmitsDebitEntry() {
        Account account = Account.open(AccountId.newId(), "owner-1", "USD");
        account.credit(Money.of(500, "USD"), TX, AT);

        LedgerEntry entry = account.debit(Money.of(200, "USD"), TX, AT);

        assertThat(account.balance()).isEqualTo(Money.of(300, "USD"));
        assertThat(entry.direction()).isEqualTo(Direction.DEBIT);
    }

    @Test
    void debitBeyondBalanceIsRejectedAndBalanceUnchanged() {
        Account account = Account.open(AccountId.newId(), "owner-1", "USD");
        account.credit(Money.of(100, "USD"), TX, AT);

        assertThatExceptionOfType(InsufficientFundsException.class)
                .isThrownBy(() -> account.debit(Money.of(101, "USD"), TX, AT));
        assertThat(account.balance()).isEqualTo(Money.of(100, "USD"));
    }

    @Test
    void nonActiveAccountRejectsMovements() {
        Account frozen = new Account(
                AccountId.newId(), "owner-1", "USD", AccountType.USER, AccountStatus.FROZEN, Money.of(100, "USD"), 0L);

        assertThatExceptionOfType(AccountNotActiveException.class)
                .isThrownBy(() -> frozen.debit(Money.of(10, "USD"), TX, AT));
        assertThatExceptionOfType(AccountNotActiveException.class)
                .isThrownBy(() -> frozen.credit(Money.of(10, "USD"), TX, AT));
    }

    @Test
    void nonPositiveAmountRejected() {
        Account account = Account.open(AccountId.newId(), "owner-1", "USD");

        assertThatThrownBy(() -> account.credit(Money.of(0, "USD"), TX, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(Money.of(-5, "USD"), TX, AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balanceCurrencyMustMatchAccountCurrency() {
        assertThatThrownBy(() -> new Account(
                        AccountId.newId(),
                        "owner-1",
                        "USD",
                        AccountType.USER,
                        AccountStatus.ACTIVE,
                        Money.of(100, "VND"),
                        0L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void systemAccountMayGoNegativeOnDebit() {
        Account systemFunding = Account.openSystem(AccountId.newId(), "USD");

        LedgerEntry entry = systemFunding.debit(Money.of(500, "USD"), TX, AT);

        assertThat(entry.direction()).isEqualTo(Direction.DEBIT);
        assertThat(systemFunding.balance()).isEqualTo(Money.of(-500, "USD"));
        assertThat(systemFunding.type()).isEqualTo(AccountType.SYSTEM);
    }
}
