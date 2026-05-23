package com.xidoke.ledger.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.xidoke.ledger.TestcontainersConfiguration;
import com.xidoke.ledger.outbox.domain.EventConsumer;
import com.xidoke.ledger.outbox.domain.OutboxRecord;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * Idempotent-consumer skeleton (LDG-55, ADR-0013): handing the same event to the consumer twice (an at-least-once
 * redelivery) records it once — the second delivery is deduped via the {@code processed_events} inbox.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "ledger.outbox.initial-delay=PT1H")
class IdempotentConsumerTest {

    @Autowired
    private EventConsumer consumer;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void redeliveredEventIsProcessedOnce() {
        long eventId = 9_000_000L + System.nanoTime() % 1_000_000L;
        OutboxRecord event = new OutboxRecord(eventId, UUID.randomUUID(), "TestEvent", "{}", 1);

        consumer.handle(event);
        consumer.handle(event); // duplicate redelivery

        assertThat(jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                        .param("id", eventId)
                        .query(Long.class)
                        .single())
                .as("the duplicate is deduped — recorded once")
                .isEqualTo(1L);
    }
}
