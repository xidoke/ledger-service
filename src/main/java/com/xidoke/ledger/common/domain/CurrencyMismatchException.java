package com.xidoke.ledger.common.domain;

/** Thrown when an operation mixes two different currencies (ADR-0008 defers multi-currency). */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String expected, String actual) {
        super("Currency mismatch: %s vs %s".formatted(expected, actual));
    }
}
