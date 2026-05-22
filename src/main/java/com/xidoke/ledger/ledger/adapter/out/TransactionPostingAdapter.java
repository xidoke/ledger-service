package com.xidoke.ledger.ledger.adapter.out;

import com.xidoke.ledger.common.domain.LedgerEntry;
import com.xidoke.ledger.ledger.domain.Transaction;
import com.xidoke.ledger.ledger.domain.TransactionRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Writes a posted transaction with {@link JdbcClient}: one INSERT for the transaction header, then an append-only
 * INSERT per ledger entry (jdbcclient-vs-jpa — append path uses JdbcClient, not JPA). {@code id}/{@code created_at} on
 * entries are filled by DB defaults.
 */
@Repository
public class TransactionPostingAdapter implements TransactionRepository {

    private final JdbcClient jdbc;

    public TransactionPostingAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Transaction transaction) {
        jdbc.sql("INSERT INTO transactions (id, type, status) VALUES (:id, :type, :status)")
                .param("id", transaction.id().value())
                .param("type", transaction.type().name())
                .param("status", transaction.status().name())
                .update();

        for (LedgerEntry entry : transaction.entries()) {
            jdbc.sql("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount) "
                            + "VALUES (:tx, :acc, :dir, :amt)")
                    .param("tx", entry.transactionId().value())
                    .param("acc", entry.accountId().value())
                    .param("dir", entry.direction().name())
                    .param("amt", entry.amount().minorUnits())
                    .update();
        }
    }
}
