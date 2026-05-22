package com.xidoke.ledger.account.adapter.out;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository over {@link AccountJpaEntity}; an implementation detail of the persistence adapter. */
interface AccountJpaEntityRepository extends JpaRepository<AccountJpaEntity, UUID> {}
