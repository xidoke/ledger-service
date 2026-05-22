package com.xidoke.ledger.transfer.adapter.in;

import com.xidoke.ledger.transfer.application.TransferResult;
import java.util.UUID;

/** Response body for a successful transfer: the posting id and both accounts' new balances. */
public record TransferResponse(
        UUID transactionId,
        UUID fromAccountId,
        long fromBalanceMinorUnits,
        UUID toAccountId,
        long toBalanceMinorUnits,
        String currency) {

    static TransferResponse from(TransferResult result) {
        return new TransferResponse(
                result.transactionId().value(),
                result.from().id().value(),
                result.from().balance().minorUnits(),
                result.to().id().value(),
                result.to().balance().minorUnits(),
                result.from().currencyCode());
    }
}
