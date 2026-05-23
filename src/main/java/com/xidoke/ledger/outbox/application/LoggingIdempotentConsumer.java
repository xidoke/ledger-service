package com.xidoke.ledger.outbox.application;

import com.xidoke.ledger.outbox.domain.EventConsumer;
import com.xidoke.ledger.outbox.domain.OutboxRecord;
import com.xidoke.ledger.outbox.domain.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Nấc-0 stand-in for a downstream consumer (ADR-0013): there is no broker yet, so "publishing" is logging what would be
 * sent. It is the idempotent-consumer skeleton — it claims each event id in the {@link ProcessedEventStore} inbox
 * first, so a redelivered (duplicate) event is skipped and the side effect runs at most once. Phase 2 replaces this
 * with a real broker consumer that keeps the same dedup-then-act shape.
 */
@Component
public class LoggingIdempotentConsumer implements EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LoggingIdempotentConsumer.class);

    private final ProcessedEventStore processed;

    public LoggingIdempotentConsumer(ProcessedEventStore processed) {
        this.processed = processed;
    }

    @Override
    public void handle(OutboxRecord event) {
        if (!processed.claim(event.id())) {
            log.info("Skipping duplicate event id={} type={}", event.id(), event.eventType());
            return;
        }
        log.info(
                "Consumed event id={} type={} aggregate={} (Nấc 0: log only — would publish to a broker at Nấc 2+)",
                event.id(),
                event.eventType(),
                event.aggregateId());
    }
}
