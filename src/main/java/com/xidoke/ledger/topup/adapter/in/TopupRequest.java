package com.xidoke.ledger.topup.adapter.in;

import jakarta.validation.constraints.Positive;

/** Request body for {@code POST /accounts/{id}/topups}. Amount in minor units (ADR-0007), must be positive. */
public record TopupRequest(@Positive long amountMinorUnits) {}
