package com.xidoke.ledger.account.adapter.in;

import com.xidoke.ledger.account.domain.Account;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Response body for an account. {@code balanceMinorUnits} is the precise integer amount (ADR-0007); clients format. */
public record AccountResponse(
        UUID id, @Nullable String ownerRef, String currency, String status, long balanceMinorUnits) {

    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id().value(),
                account.ownerRef(),
                account.currencyCode(),
                account.status().name(),
                account.balance().minorUnits());
    }
}
