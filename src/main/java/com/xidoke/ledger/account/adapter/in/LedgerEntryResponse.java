package com.xidoke.ledger.account.adapter.in;

import com.xidoke.ledger.common.domain.LedgerEntry;
import java.time.Instant;
import java.util.UUID;

/** Response body for one ledger entry in an account's history. */
public record LedgerEntryResponse(
        UUID transactionId, String direction, long amountMinorUnits, String currency, Instant createdAt) {

    static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.transactionId().value(),
                entry.direction().name(),
                entry.amount().minorUnits(),
                entry.amount().currencyCode(),
                entry.createdAt());
    }
}
