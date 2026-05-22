package com.xidoke.ledger.account.domain;

import com.xidoke.ledger.common.domain.AccountId;
import java.util.UUID;

/** Well-known identities for system accounts seeded by migration (ADR-0009). */
public final class SystemAccounts {

    /** The funding counterpart for top-ups; seeded in V5. {@code owner_ref} is null; balance may go negative. */
    public static final AccountId SYSTEM_FUNDING_ID =
            AccountId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private SystemAccounts() {}
}
