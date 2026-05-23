package com.xidoke.ledger.outbox.adapter.in;

import com.xidoke.ledger.outbox.domain.EventConsumer;
import com.xidoke.ledger.outbox.domain.OutboxRecord;
import com.xidoke.ledger.outbox.domain.OutboxRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the transactional outbox (ADR-0013, read side). On each tick it locks a batch of PENDING events ({@code FOR
 * UPDATE SKIP LOCKED}), hands each to the {@link EventConsumer}, and marks it SENT — all in one transaction, so the
 * rows it processed stay locked until commit and a crash mid-batch simply leaves them PENDING for the next tick
 * (at-least-once). A single event whose publish throws is left PENDING (logged) without failing the rest of the batch.
 * Delivery is at-least-once, which is why the consumer is idempotent.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outbox;
    private final EventConsumer consumer;
    private final int batchSize;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Repositories/consumers are stateless Spring-managed singletons injected by the container")
    public OutboxPoller(
            OutboxRepository outbox, EventConsumer consumer, @Value("${ledger.outbox.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.consumer = consumer;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${ledger.outbox.poll-interval:PT1S}",
            initialDelayString = "${ledger.outbox.initial-delay:PT2S}")
    @Transactional
    public void poll() {
        List<OutboxRecord> batch = outbox.fetchPendingBatch(batchSize);
        for (OutboxRecord event : batch) {
            try {
                consumer.handle(event);
                outbox.markSent(event.id());
            } catch (RuntimeException e) {
                log.warn("Publish failed for outbox event id={}; leaving PENDING for the next poll", event.id(), e);
            }
        }
    }
}
