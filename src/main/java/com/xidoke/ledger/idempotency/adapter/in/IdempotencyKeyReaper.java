package com.xidoke.ledger.idempotency.adapter.in;

import com.xidoke.ledger.idempotency.domain.IdempotencyStore;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Time-triggered inbound adapter (ADR-0012) that reaps expired {@code idempotency_keys} rows. The happy-path
 * {@code release} on a failed operation (LDG-49) cannot cover a process crash <i>between</i> claim and complete: that
 * leaves a PENDING row orphaned, and every retry with the same key would then get {@code 409} forever. This sweep caps
 * that by deleting PENDING rows older than a short max-in-flight window (a real request never runs that long) and
 * COMPLETED rows past the client retry window (Stripe's 24h), after which a key is safely reusable.
 *
 * <p>The DELETE is idempotent, so on a future multi-instance deployment every instance running it is harmless (just
 * redundant); single-instance at Nấc 0-1 makes that a non-issue.
 */
@Component
public class IdempotencyKeyReaper {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyReaper.class);

    private final IdempotencyStore store;
    private final Duration pendingTtl;
    private final Duration completedTtl;

    public IdempotencyKeyReaper(
            IdempotencyStore store,
            @Value("${ledger.idempotency.reaper.pending-ttl:PT10M}") Duration pendingTtl,
            @Value("${ledger.idempotency.reaper.completed-ttl:PT24H}") Duration completedTtl) {
        this.store = store;
        this.pendingTtl = pendingTtl;
        this.completedTtl = completedTtl;
    }

    @Scheduled(fixedDelayString = "${ledger.idempotency.reaper.interval:PT10M}")
    public void sweep() {
        Instant now = Instant.now();
        int swept = store.sweepExpired(now.minus(pendingTtl), now.minus(completedTtl));
        if (swept > 0) {
            log.info("Idempotency reaper swept {} expired key(s)", swept);
        }
    }
}
