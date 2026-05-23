package com.xidoke.ledger.idempotency.adapter.out;

import com.xidoke.ledger.idempotency.domain.IdempotencyRecord;
import com.xidoke.ledger.idempotency.domain.IdempotencyStatus;
import com.xidoke.ledger.idempotency.domain.IdempotencyStore;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link IdempotencyStore} over {@link JdbcClient} — the idempotency table is infrastructure CRUD with no domain
 * aggregate, so it uses JdbcClient directly (the ADR-0018 pragmatic exception, like the ledger append path). The claim
 * is an atomic {@code INSERT … ON CONFLICT DO NOTHING}; the unique primary key serializes concurrent same-key requests.
 */
@Repository
public class IdempotencyJdbcStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    public IdempotencyJdbcStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String key, String requestHash) {
        int rows = jdbc.sql("INSERT INTO idempotency_keys (key, request_hash, status) "
                        + "VALUES (:key, :hash, 'PENDING') ON CONFLICT (key) DO NOTHING")
                .param("key", key)
                .param("hash", requestHash)
                .update();
        return rows == 1;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        return jdbc.sql("SELECT key, request_hash, status, response_status, response_body, created_at "
                        + "FROM idempotency_keys WHERE key = :key")
                .param("key", key)
                .query((rs, rowNum) -> new IdempotencyRecord(
                        rs.getString("key"),
                        rs.getString("request_hash"),
                        IdempotencyStatus.valueOf(rs.getString("status")),
                        rs.getObject("response_status", Integer.class),
                        rs.getString("response_body"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    @Override
    public void complete(String key, int responseStatus, String responseBody) {
        jdbc.sql("UPDATE idempotency_keys SET status = 'COMPLETED', response_status = :status, response_body = :body "
                        + "WHERE key = :key")
                .param("status", responseStatus)
                .param("body", responseBody)
                .param("key", key)
                .update();
    }

    @Override
    public void release(String key) {
        jdbc.sql("DELETE FROM idempotency_keys WHERE key = :key AND status = 'PENDING'")
                .param("key", key)
                .update();
    }
}
