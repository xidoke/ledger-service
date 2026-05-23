package com.xidoke.ledger.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.xidoke.ledger.TestcontainersConfiguration;
import com.xidoke.ledger.outbox.adapter.in.OutboxPoller;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * Outbox poller (LDG-55, ADR-0013): a poll drains every PENDING event to SENT exactly once and stamps
 * {@code published_at}; a second poll is a no-op (nothing reprocessed). {@code initial-delay} is pushed out so the
 * scheduler never auto-fires during the test — {@link OutboxPoller#poll()} is driven manually.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "ledger.outbox.initial-delay=PT1H")
class OutboxPollerTest {

    @Autowired
    private OutboxPoller poller;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void pollMarksPendingEventsSentExactlyOnce() {
        seedPendingEvent();
        seedPendingEvent();
        seedPendingEvent();
        long pendingBefore = count("SELECT count(*) FROM outbox WHERE status = 'PENDING'");
        assertThat(pendingBefore).isGreaterThanOrEqualTo(3);

        poller.poll();

        assertThat(count("SELECT count(*) FROM outbox WHERE status = 'PENDING'"))
                .as("all pending drained")
                .isZero();
        assertThat(count("SELECT count(*) FROM outbox WHERE status = 'SENT' AND published_at IS NULL"))
                .as("every SENT row has a published_at stamp")
                .isZero();
        long processedAfterFirst = count("SELECT count(*) FROM processed_events");
        assertThat(processedAfterFirst).as("each event consumed once").isEqualTo(pendingBefore);

        // a second poll has nothing PENDING → no reprocessing
        poller.poll();
        assertThat(count("SELECT count(*) FROM processed_events"))
                .as("second poll reprocesses nothing")
                .isEqualTo(processedAfterFirst);
    }

    private void seedPendingEvent() {
        jdbc.sql("INSERT INTO outbox (aggregate_id, event_type, payload, schema_version) "
                        + "VALUES (:agg, 'TestEvent', CAST('{}' AS jsonb), 1)")
                .param("agg", UUID.randomUUID())
                .update();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
