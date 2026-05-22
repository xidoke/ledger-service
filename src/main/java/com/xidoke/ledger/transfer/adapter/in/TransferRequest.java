package com.xidoke.ledger.transfer.adapter.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Request body for {@code POST /transfers}. Both account ids are required; amount is in minor units (ADR-0007) and must
 * be positive. Same-account and currency-mismatch rejections are business rules handled in the service.
 */
public record TransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @Positive long amountMinorUnits) {}
