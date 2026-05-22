package com.xidoke.ledger.account.adapter.out;

import com.xidoke.ledger.account.domain.LedgerEntryQueryRepository;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Direction;
import com.xidoke.ledger.common.domain.LedgerEntry;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-side adapter for account entry history, using {@link JdbcClient} (not JPA): append-only entries are read with
 * explicit SQL and no ORM surprises (jdbcclient-vs-jpa). Entries come back newest-first.
 */
@Repository
public class LedgerEntryQueryJdbcAdapter implements LedgerEntryQueryRepository {

    private final JdbcClient jdbc;

    public LedgerEntryQueryJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LedgerEntry> findByAccount(AccountId accountId, String currencyCode) {
        return jdbc.sql("""
                        SELECT transaction_id, account_id, direction, amount, created_at
                        FROM ledger_entries
                        WHERE account_id = :accountId
                        ORDER BY created_at DESC
                        """)
                .param("accountId", accountId.value())
                .query((rs, rowNum) -> new LedgerEntry(
                        TransactionId.of(rs.getObject("transaction_id", UUID.class)),
                        AccountId.of(rs.getObject("account_id", UUID.class)),
                        Direction.valueOf(rs.getString("direction")),
                        Money.of(rs.getLong("amount"), currencyCode),
                        // pgjdbc maps timestamptz to OffsetDateTime, not Instant directly
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
