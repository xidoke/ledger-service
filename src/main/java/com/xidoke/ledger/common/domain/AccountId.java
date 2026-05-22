package com.xidoke.ledger.common.domain;

import java.util.Objects;
import java.util.UUID;

/** Type-safe identity of an {@code Account}. Wrapping {@link UUID} prevents mixing it with other ids. */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }
}
