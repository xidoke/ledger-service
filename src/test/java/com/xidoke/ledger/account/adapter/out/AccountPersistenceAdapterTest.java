package com.xidoke.ledger.account.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.xidoke.ledger.TestcontainersConfiguration;
import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.account.domain.AccountRepository;
import com.xidoke.ledger.account.domain.AccountStatus;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Roundtrips the {@link Account} aggregate through the JPA adapter against a real Postgres (Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountPersistenceAdapterTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void savesAndReadsBackAnAccount() {
        AccountId id = AccountId.newId();
        Account account = Account.open(id, "owner-1", "USD");
        account.credit(Money.of(500, "USD"), TransactionId.newId(), Instant.now());

        accountRepository.save(account);
        Optional<Account> loaded = accountRepository.findById(id);

        assertThat(loaded).isPresent();
        Account reloaded = loaded.get();
        assertThat(reloaded.id()).isEqualTo(id);
        assertThat(reloaded.ownerRef()).isEqualTo("owner-1");
        assertThat(reloaded.currencyCode()).isEqualTo("USD");
        assertThat(reloaded.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reloaded.balance()).isEqualTo(Money.of(500, "USD"));
    }

    @Test
    void findByIdReturnsEmptyForUnknownAccount() {
        assertThat(accountRepository.findById(AccountId.newId())).isEmpty();
    }
}
