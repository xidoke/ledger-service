package com.xidoke.ledger.account.adapter.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Request body for {@code POST /accounts}. {@code ownerRef} is optional (null for system accounts). */
public record CreateAccountRequest(
        @Nullable String ownerRef,
        @NotBlank @Size(min = 3, max = 3) String currency) {}
