package com.xidoke.ledger.account.adapter.out;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.account.domain.AccountRepository;
import com.xidoke.ledger.common.domain.AccountId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Outbound adapter implementing the {@link AccountRepository} port with Spring Data JPA. Translates domain ↔ JPA entity
 * via {@link AccountJpaMapper} so neither layer leaks into the other (hexagonal, ADR-0018).
 */
@Repository
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaEntityRepository entities;

    public AccountPersistenceAdapter(AccountJpaEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public Account save(Account account) {
        return AccountJpaMapper.toDomain(entities.save(AccountJpaMapper.toEntity(account)));
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return entities.findById(id.value()).map(AccountJpaMapper::toDomain);
    }
}
