package com.xidoke.ledger.common.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Monetary amount as integer minor units plus an ISO 4217 currency code (ADR-0007). Immutable value object; arithmetic
 * is exact {@code long} math with overflow protection. Display formatting (decimal conversion) belongs at the
 * presentation layer via {@link #toDecimal(int)}.
 */
public record Money(long minorUnits, String currencyCode) {

    public Money {
        Objects.requireNonNull(currencyCode, "currencyCode");
        if (currencyCode.length() != 3) {
            throw new IllegalArgumentException("currencyCode must be a 3-letter ISO 4217 code: " + currencyCode);
        }
    }

    public static Money of(long minorUnits, String currencyCode) {
        return new Money(minorUnits, currencyCode);
    }

    public static Money zero(String currencyCode) {
        return new Money(0L, currencyCode);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currencyCode);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currencyCode);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currencyCode);
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isPositive() {
        return minorUnits > 0L;
    }

    public boolean isNegative() {
        return minorUnits < 0L;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return minorUnits < other.minorUnits;
    }

    public BigDecimal toDecimal(int scale) {
        return BigDecimal.valueOf(minorUnits, scale);
    }

    private void assertSameCurrency(Money other) {
        if (!currencyCode.equals(other.currencyCode)) {
            throw new CurrencyMismatchException(currencyCode, other.currencyCode);
        }
    }
}
