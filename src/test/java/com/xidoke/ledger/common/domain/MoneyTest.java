package com.xidoke.ledger.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addAndSubtractAreExactLongMath() {
        Money a = Money.of(1234, "USD");
        Money b = Money.of(766, "USD");

        assertThat(a.add(b)).isEqualTo(Money.of(2000, "USD"));
        assertThat(a.subtract(b)).isEqualTo(Money.of(468, "USD"));
    }

    @Test
    void negateFlipsSign() {
        assertThat(Money.of(500, "USD").negate()).isEqualTo(Money.of(-500, "USD"));
    }

    @Test
    void signPredicates() {
        assertThat(Money.of(0, "USD").isZero()).isTrue();
        assertThat(Money.of(1, "USD").isPositive()).isTrue();
        assertThat(Money.of(-1, "USD").isNegative()).isTrue();
    }

    @Test
    void isLessThanComparesAmount() {
        assertThat(Money.of(100, "USD").isLessThan(Money.of(101, "USD"))).isTrue();
        assertThat(Money.of(100, "USD").isLessThan(Money.of(100, "USD"))).isFalse();
    }

    @Test
    void mixingCurrenciesThrows() {
        Money usd = Money.of(100, "USD");
        Money vnd = Money.of(100, "VND");

        assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> usd.add(vnd));
        assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> usd.subtract(vnd));
        assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> usd.isLessThan(vnd));
    }

    @Test
    void invalidCurrencyCodeRejected() {
        assertThatThrownBy(() -> Money.of(100, "US")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowIsDetectedNotWrapped() {
        Money max = Money.of(Long.MAX_VALUE, "USD");
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> max.add(Money.of(1, "USD")));
    }

    @Test
    void toDecimalConvertsForDisplay() {
        assertThat(Money.of(1234, "USD").toDecimal(2)).isEqualByComparingTo(new BigDecimal("12.34"));
    }

    @Test
    void valueEqualityIgnoresIdentity() {
        assertThat(Money.of(100, "USD")).isEqualTo(Money.of(100, "USD"));
    }
}
