package com.xidoke.ledger.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.TestcontainersConfiguration;
import com.xidoke.ledger.common.concurrency.OptimisticRetry;
import com.xidoke.ledger.common.domain.AccountId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrency-control <b>benchmark</b> backing ADR-0011 — optimistic locking ({@code @Version}) + bounded retry vs.
 * pessimistic {@code SELECT … FOR UPDATE}, on the identical {@link TransferService} body. This is a timing experiment,
 * not a correctness test (correctness is {@code TransferConcurrencyStressTest}); it is tagged {@code benchmark} and
 * excluded from the normal/CI build. Run it on demand:
 *
 * <pre>./mvnw test -Pbenchmark</pre>
 *
 * <p>Two scenarios are measured, each driving {@value N} concurrent transfers:
 *
 * <ul>
 *   <li><b>High contention</b> — every transfer debits the <i>same</i> source row (the hot-account shape). Optimistic
 *       writers collide and burn retries; the pessimistic writers serialize on the row lock.
 *   <li><b>Low contention</b> — {@value N} disjoint source→dest pairs, no shared row. Optimistic never conflicts;
 *       pessimistic locks are uncontended.
 * </ul>
 *
 * <p>Both strategies complete all {@value N} transfers ({@code max-attempts} is raised so optimistic never exhausts),
 * so wall-clock is comparable work-for-work. The optimistic arm also reports total attempts (attempts − successes =
 * wasted retry work) by counting how many times the retried action is invoked.
 */
@Tag("benchmark")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            // raise the cap so even the hot-row scenario lets every writer eventually win → equal work both arms
            "ledger.retry.max-attempts=200",
            // don't let the connection pool, rather than the locking strategy, cap real concurrency
            "spring.datasource.hikari.maximum-pool-size=64"
        })
class TransferConcurrencyBenchmark {

    private static final Logger log = LoggerFactory.getLogger(TransferConcurrencyBenchmark.class);
    private static final int N = 50;
    private static final long AMOUNT = 100;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransferService transferService;

    @Autowired
    private OptimisticRetry retry;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager em;

    private final AtomicLong optimisticAttempts = new AtomicLong();

    @FunctionalInterface
    private interface TransferOp {
        void run(UUID from, UUID to);
    }

    /** Production path: bounded-retry around the real transfer; the counter ticks once per attempt. */
    private void optimistic(UUID from, UUID to) {
        retry.execute(() -> {
            optimisticAttempts.incrementAndGet();
            return transferService.transfer(AccountId.of(from), AccountId.of(to), AMOUNT);
        });
    }

    /** Same transfer body, but the two rows are pessimistically locked (id-ordered to avoid deadlock) first. */
    private void pessimistic(UUID from, UUID to) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            UUID first = from.compareTo(to) <= 0 ? from : to;
            UUID second = from.compareTo(to) <= 0 ? to : from;
            lockForUpdate(first);
            lockForUpdate(second);
            transferService.transfer(AccountId.of(from), AccountId.of(to), AMOUNT);
        });
    }

    private void lockForUpdate(UUID id) {
        em.createNativeQuery("SELECT 1 FROM accounts WHERE id = ?1 FOR UPDATE")
                .setParameter(1, id)
                .getResultList();
    }

    @Test
    void highContention() throws Exception {
        log.info("=== ADR-0011 BENCHMARK · HIGH CONTENTION (N={} transfers from ONE source row) ===", N);
        Result opt = runHighContention(this::optimistic);
        Result pess = runHighContention(this::pessimistic);
        report("high-contention", opt, pess);
        assertThat(opt.successes)
                .as("optimistic completes all under raised cap")
                .isEqualTo(N);
        assertThat(pess.successes).as("pessimistic completes all").isEqualTo(N);
    }

    @Test
    void lowContention() throws Exception {
        log.info("=== ADR-0011 BENCHMARK · LOW CONTENTION (N={} disjoint source→dest pairs) ===", N);
        Result opt = runLowContention(this::optimistic);
        Result pess = runLowContention(this::pessimistic);
        report("low-contention", opt, pess);
        assertThat(opt.successes).isEqualTo(N);
        assertThat(pess.successes).isEqualTo(N);
    }

    private Result runHighContention(TransferOp op) throws Exception {
        UUID from = createAccount();
        UUID to = createAccount();
        topup(from, (long) N * AMOUNT);
        List<UUID[]> pairs = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            pairs.add(new UUID[] {from, to});
        }
        Result r = runConcurrent(pairs, op);
        // sanity: the cached balance must still equal the ledger truth (correctness is asserted in full by the stress
        // test)
        assertThat(balanceCache(from)).isEqualTo(ledgerBalance(from));
        return r;
    }

    private Result runLowContention(TransferOp op) throws Exception {
        List<UUID[]> pairs = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            UUID source = createAccount();
            UUID dest = createAccount();
            topup(source, AMOUNT);
            pairs.add(new UUID[] {source, dest});
        }
        return runConcurrent(pairs, op);
    }

    private Result runConcurrent(List<UUID[]> pairs, TransferOp op) throws InterruptedException {
        optimisticAttempts.set(0);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(pairs.size());
        ExecutorService pool = Executors.newFixedThreadPool(pairs.size());
        for (UUID[] pair : pairs) {
            pool.submit(() -> {
                try {
                    start.await();
                    op.run(pair[0], pair[1]);
                    ok.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        long t0 = System.nanoTime();
        start.countDown();
        done.await();
        long millis = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdownNow();
        return new Result(millis, ok.get(), failed.get(), optimisticAttempts.get());
    }

    private void report(String scenario, Result opt, Result pess) {
        log.info(
                "[{}] optimistic+retry      : {} ms | {} ok, {} failed | {} attempts (retry waste = {})",
                scenario,
                opt.millis,
                opt.successes,
                opt.failures,
                opt.attempts,
                opt.attempts - opt.successes);
        log.info(
                "[{}] pessimistic FOR UPDATE: {} ms | {} ok, {} failed",
                scenario,
                pess.millis,
                pess.successes,
                pess.failures);
    }

    private record Result(long millis, int successes, int failures, long attempts) {}

    private UUID createAccount() throws Exception {
        String json = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerRef\":\"owner\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private void topup(UUID id, long amount) throws Exception {
        mockMvc.perform(post("/accounts/{id}/topups", id)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":%d}".formatted(amount)))
                .andExpect(status().isCreated());
    }

    private long balanceCache(UUID id) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }

    private long ledgerBalance(UUID id) {
        return jdbc.sql("SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)"
                        + " FROM ledger_entries WHERE account_id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }
}
