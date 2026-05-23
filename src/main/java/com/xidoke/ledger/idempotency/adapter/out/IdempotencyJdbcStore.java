package com.xidoke.ledger.idempotency.adapter.out;

import com.xidoke.ledger.idempotency.domain.IdempotencyRecord;
import com.xidoke.ledger.idempotency.domain.IdempotencyStore;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link IdempotencyStore} over {@link JdbcClient} — the idempotency table is infrastructure CRUD with no domain
 * aggregate, so it uses JdbcClient directly (the ADR-0018 pragmatic exception, like the ledger append path).
 */
@Repository
public class IdempotencyJdbcStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    public IdempotencyJdbcStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        return jdbc.sql("SELECT key, request_hash, response_status, response_body, created_at "
                        + "FROM idempotency_keys WHERE key = :key")
                .param("key", key)
                .query((rs, rowNum) -> new IdempotencyRecord(
                        rs.getString("key"),
                        rs.getString("request_hash"),
                        rs.getInt("response_status"),
                        rs.getString("response_body"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    @Override
    public void save(IdempotencyRecord record) {
        jdbc.sql("INSERT INTO idempotency_keys (key, request_hash, response_status, response_body) "
                        + "VALUES (:key, :hash, :status, :body)")
                .param("key", record.key())
                .param("hash", record.requestHash())
                .param("status", record.responseStatus())
                .param("body", record.responseBody())
                .update();
    }
}
