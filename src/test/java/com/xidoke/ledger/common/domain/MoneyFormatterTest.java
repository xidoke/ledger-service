package com.xidoke.ledger.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyFormatterTest {

    @Test
    void formatsTwoFractionDigitCurrency() {
        assertThat(MoneyFormatter.format(Money.of(1234, "USD"))).isEqualTo("12.34 USD");
        assertThat(MoneyFormatter.toMajorUnits(Money.of(1234, "USD"))).isEqualByComparingTo(new BigDecimal("12.34"));
    }

    @Test
    void formatsZeroFractionDigitCurrencies() {
        assertThat(MoneyFormatter.format(Money.of(1000, "VND"))).isEqualTo("1000 VND");
        assertThat(MoneyFormatter.format(Money.of(500, "JPY"))).isEqualTo("500 JPY");
    }

    @Test
    void formatsNegativeAndZero() {
        assertThat(MoneyFormatter.format(Money.of(-1234, "USD"))).isEqualTo("-12.34 USD");
        assertThat(MoneyFormatter.format(Money.zero("USD"))).isEqualTo("0.00 USD");
    }

    @Test
    void unknownCurrencyCodeIsRejected() {
        assertThatThrownBy(() -> MoneyFormatter.format(Money.of(100, "USX")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
