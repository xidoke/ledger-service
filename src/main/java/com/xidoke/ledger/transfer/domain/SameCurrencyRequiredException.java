package com.xidoke.ledger.transfer.domain;

import com.xidoke.ledger.common.error.UnprocessableEntityException;

/**
 * Thrown when a transfer is attempted between accounts of different currencies. Phase 1 is single-currency (ADR-0008);
 * cross-currency transfers need explicit FX conversion entries, which are out of scope until multi-currency lands.
 */
public class SameCurrencyRequiredException extends UnprocessableEntityException {

    public SameCurrencyRequiredException(String fromCurrency, String toCurrency) {
        super("Transfer requires matching currencies: from=%s, to=%s".formatted(fromCurrency, toCurrency));
    }
}
