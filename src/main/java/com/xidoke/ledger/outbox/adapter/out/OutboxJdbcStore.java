package com.xidoke.ledger.outbox.adapter.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.outbox.domain.OutboxRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link OutboxRepository} over {@link JdbcClient} — the outbox is infrastructure CRUD with no domain aggregate, so it
 * uses JdbcClient directly (the ADR-0018 pragmatic exception, like the ledger append + idempotency store). The INSERT
 * runs in the caller's transaction, so it commits with the ledger write or not at all.
 */
@Repository
public class OutboxJdbcStore implements OutboxRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "ObjectMapper is a shared, effectively-immutable Spring singleton injected by the container")
    public OutboxJdbcStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(UUID aggregateId, String eventType, Object payload, int schemaVersion) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize outbox payload for event " + eventType, e);
        }
        jdbc.sql("INSERT INTO outbox (aggregate_id, event_type, payload, schema_version) "
                        + "VALUES (:agg, :type, CAST(:payload AS jsonb), :ver)")
                .param("agg", aggregateId)
                .param("type", eventType)
                .param("payload", json)
                .param("ver", schemaVersion)
                .update();
    }
}
