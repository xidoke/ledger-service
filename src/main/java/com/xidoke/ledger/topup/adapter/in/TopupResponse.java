package com.xidoke.ledger.topup.adapter.in;

import com.xidoke.ledger.topup.application.TopupResult;
import java.util.UUID;

/** Response body for a successful top-up: the posting id and the account's new balance. */
public record TopupResponse(UUID transactionId, UUID accountId, long balanceMinorUnits, String currency) {

    static TopupResponse from(TopupResult result) {
        return new TopupResponse(
                result.transactionId().value(),
                result.account().id().value(),
                result.account().balance().minorUnits(),
                result.account().currencyCode());
    }
}
