package com.xidoke.ledger.common.concurrency;

import com.xidoke.ledger.common.error.ConcurrencyConflictException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Bounded retry around an optimistically-locked write (ADR-0011). The {@code action} must be a self-contained,
 * {@code @Transactional} call: each attempt runs in a <b>fresh transaction</b> (so it reloads the row at its current
 * {@code @Version}), and a lost optimistic-lock race surfaces as {@link OptimisticLockingFailureException} at commit.
 * The caller (a controller) therefore invokes the service method through its Spring proxy inside {@link #execute}, not
 * a self-invocation.
 *
 * <p>Retries are <b>capped</b> with exponential backoff + full jitter — never an unbounded spin (that would livelock a
 * hot account, see {@code antipattern-cas-loop-without-retry}). When the cap is hit the conflict is genuine contention,
 * surfaced as {@link ConcurrencyConflictException} → HTTP 409. Retry only resolves <i>moderate</i> contention; an
 * extreme hot account (e.g. every top-up debiting SYSTEM_FUNDING) needs sharding/async, not more retries.
 */
@Component
public class OptimisticRetry {

    private static final Logger log = LoggerFactory.getLogger(OptimisticRetry.class);

    private final int maxAttempts;
    private final long baseBackoffMillis;
    private final long maxBackoffMillis;

    public OptimisticRetry(
            @Value("${ledger.retry.max-attempts:5}") int maxAttempts,
            @Value("${ledger.retry.base-backoff-millis:25}") long baseBackoffMillis,
            @Value("${ledger.retry.max-backoff-millis:200}") long maxBackoffMillis) {
        this.maxAttempts = maxAttempts;
        this.baseBackoffMillis = baseBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
    }

    /** Runs {@code action}, retrying on optimistic-lock conflict up to {@code maxAttempts}; then throws 409. */
    public <T> T execute(Supplier<T> action) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException e) {
                last = e;
                if (attempt < maxAttempts) {
                    backoff(attempt);
                }
            }
        }
        throw new ConcurrencyConflictException(
                "Could not apply the write after %d attempts due to concurrent updates".formatted(maxAttempts), last);
    }

    private void backoff(int attempt) {
        long ceiling = Math.min(maxBackoffMillis, baseBackoffMillis * (1L << (attempt - 1)));
        long sleep = ThreadLocalRandom.current().nextLong(ceiling + 1); // full jitter in [0, ceiling]
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrencyConflictException("Interrupted while backing off before an optimistic-lock retry", e);
        }
    }
}
