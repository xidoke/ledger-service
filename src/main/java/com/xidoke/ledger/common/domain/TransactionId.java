package com.xidoke.ledger.common.domain;

import java.util.Objects;
import java.util.UUID;

/** Type-safe identity of a {@code Transaction}. Wrapping {@link UUID} prevents mixing it with other ids. */
public record TransactionId(UUID value) {

    public TransactionId {
        Objects.requireNonNull(value, "value");
    }

    public static TransactionId of(UUID value) {
        return new TransactionId(value);
    }

    public static TransactionId newId() {
        return new TransactionId(UUID.randomUUID());
    }
}
