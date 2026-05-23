package com.xidoke.ledger.ledger.adapter.out;

import com.xidoke.ledger.ledger.domain.BalanceDrift;
import com.xidoke.ledger.ledger.domain.ReconciliationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ReconciliationRepository} over {@link JdbcClient}. These are cross-aggregate reporting reads (they aggregate
 * {@code ledger_entries} against {@code accounts}), so JdbcClient is used directly (ADR-0018 pragmatic exception) —
 * reading the tables, not another feature's classes, so the hexagonal boundaries stay intact.
 */
@Repository
public class ReconciliationJdbcAdapter implements ReconciliationRepository {

    private final JdbcClient jdbc;

    public ReconciliationJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BalanceDrift> findBalanceDrift() {
        return jdbc.sql("""
                        SELECT a.id AS account_id,
                               a.balance AS cached,
                               COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) AS ledger
                        FROM accounts a
                        LEFT JOIN ledger_entries e ON e.account_id = a.id
                        GROUP BY a.id, a.balance
                        HAVING a.balance <> COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
                        """)
                .query((rs, rowNum) -> new BalanceDrift(
                        rs.getObject("account_id", UUID.class), rs.getLong("cached"), rs.getLong("ledger")))
                .list();
    }

    @Override
    public long trialBalanceImbalance() {
        return jdbc.sql("SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END), 0) "
                        + "FROM ledger_entries")
                .query(Long.class)
                .single();
    }
}
