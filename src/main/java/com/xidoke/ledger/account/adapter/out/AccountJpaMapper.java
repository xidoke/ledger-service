package com.xidoke.ledger.account.adapter.out;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.account.domain.AccountStatus;
import com.xidoke.ledger.account.domain.AccountType;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Money;

/** Maps between the domain {@link Account} aggregate and its {@link AccountJpaEntity} persistence model. */
final class AccountJpaMapper {

    private AccountJpaMapper() {}

    static AccountJpaEntity toEntity(Account account) {
        return new AccountJpaEntity(
                account.id().value(),
                account.ownerRef(),
                account.currencyCode(),
                account.status().name(),
                account.type().name(),
                account.balance().minorUnits(),
                account.version());
    }

    static Account toDomain(AccountJpaEntity entity) {
        return new Account(
                AccountId.of(entity.getId()),
                entity.getOwnerRef(),
                entity.getCurrency(),
                AccountType.valueOf(entity.getAccountType()),
                AccountStatus.valueOf(entity.getStatus()),
                Money.of(entity.getBalance(), entity.getCurrency()),
                entity.getVersion());
    }
}
