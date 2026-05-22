package com.xidoke.ledger.account.domain;

import com.xidoke.ledger.common.domain.AccountId;
import java.util.Optional;

/**
 * Outbound port for persisting the {@link Account} aggregate (hexagonal, ADR-0018). The domain depends only on this
 * interface; the JPA implementation lives in {@code adapter/out} and never leaks back into the domain.
 */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(AccountId id);
}
