package com.xidoke.ledger.outbox.adapter.out;

import com.xidoke.ledger.outbox.domain.ProcessedEventStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ProcessedEventStore} over {@link JdbcClient}. The claim is an atomic {@code INSERT … ON CONFLICT DO NOTHING}
 * on the {@code processed_events} PK, so two deliveries of the same event id race safely — exactly one inserts a row.
 */
@Repository
public class ProcessedEventJdbcStore implements ProcessedEventStore {

    private final JdbcClient jdbc;

    public ProcessedEventJdbcStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(long eventId) {
        return jdbc.sql("INSERT INTO processed_events (event_id) VALUES (:id) ON CONFLICT DO NOTHING")
                        .param("id", eventId)
                        .update()
                == 1;
    }
}
