package com.xidoke.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Exercises the V2–V4 migrations against a real Postgres (Testcontainers): the schema applies, a balanced posting can
 * be written, and the append-only invariant + CHECK constraints are enforced at the DB layer.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LedgerSchemaMigrationTest {

    @Autowired
    private JdbcClient jdbc;

    private UUID openAccount() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO accounts (id, owner_ref, currency, status) VALUES (:id, :owner, :ccy, 'ACTIVE')")
                .param("id", id)
                .param("owner", "owner-" + id)
                .param("ccy", "USD")
                .update();
        return id;
    }

    private UUID openTransaction() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO transactions (id, type, status) VALUES (:id, 'TRANSFER', 'POSTED')")
                .param("id", id)
                .update();
        return id;
    }

    private UUID insertEntry(UUID txId, UUID accountId, String direction, long amount) {
        return jdbc.sql("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount) "
                        + "VALUES (:tx, :acc, :dir, :amt) RETURNING id")
                .param("tx", txId)
                .param("acc", accountId)
                .param("dir", direction)
                .param("amt", amount)
                .query(UUID.class)
                .single();
    }

    @Test
    void balancedPostingCanBeWritten() {
        UUID account = openAccount();
        UUID tx = openTransaction();

        insertEntry(tx, account, "DEBIT", 1000);
        insertEntry(tx, account, "CREDIT", 1000);

        Long count = jdbc.sql("SELECT count(*) FROM ledger_entries WHERE transaction_id = :tx")
                .param("tx", tx)
                .query(Long.class)
                .single();
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void ledgerEntriesAreAppendOnly() {
        UUID account = openAccount();
        UUID tx = openTransaction();
        UUID entryId = insertEntry(tx, account, "DEBIT", 500);

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbc.sql("UPDATE ledger_entries SET amount = 999 WHERE id = :id")
                        .param("id", entryId)
                        .update());

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbc.sql("DELETE FROM ledger_entries WHERE id = :id")
                        .param("id", entryId)
                        .update());
    }

    @Test
    void nonPositiveAmountIsRejected() {
        UUID account = openAccount();
        UUID tx = openTransaction();

        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> insertEntry(tx, account, "DEBIT", 0));
    }

    @Test
    void invalidAccountStatusIsRejected() {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbc.sql("INSERT INTO accounts (id, currency, status) VALUES (:id, 'USD', 'BOGUS')")
                        .param("id", UUID.randomUUID())
                        .update());
    }
}
