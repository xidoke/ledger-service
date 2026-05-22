package com.xidoke.ledger.common.domain;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Currency-aware display formatting for {@link Money}. Converts integer minor units to major units using the ISO 4217
 * fraction digits of the currency (USD → 2, VND/JPY → 0), so the {@code minor → major} scaling lives in exactly one
 * place — never a stray {@code / 100} (ADR-0007).
 *
 * <p>Backend-oriented: emits a plain decimal plus the currency code (e.g. {@code "12.34 USD"}). Locale-aware symbols
 * and grouping are a presentation concern and are intentionally out of scope here.
 */
public final class MoneyFormatter {

    private MoneyFormatter() {}

    /** Amount in major units, scaled per the currency's ISO 4217 fraction digits. */
    public static BigDecimal toMajorUnits(Money money) {
        return money.toDecimal(fractionDigits(money.currencyCode()));
    }

    /** Backend display string, e.g. {@code "12.34 USD"} or {@code "1000 VND"}. */
    public static String format(Money money) {
        return toMajorUnits(money).toPlainString() + " " + money.currencyCode();
    }

    private static int fractionDigits(String currencyCode) {
        int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        // Pseudo-currencies (e.g. XAU gold) report -1 — treat as no minor unit.
        return Math.max(digits, 0);
    }
}
