package com.xidoke.ledger.transfer.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Concurrency-safety stress test (M4): fire {@value N} transfers at the same source account at once and assert the
 * books are never corrupted. The source row is the hot spot; optimistic locking (the {@code @Version} column on
 * {@code accounts}, ADR-0011) is what makes the cached balance stay consistent with the append-only ledger entries
 * under that contention.
 *
 * <p>What this guards (all timing-independent — true for any success/failure split):
 *
 * <ul>
 *   <li><b>No lost update</b> — each account's cached {@code balance} equals the signed sum of its ledger entries.
 *   <li><b>No negative balance.</b>
 *   <li><b>Exact accounting</b> — the number of postings equals the number of {@code 201} responses; a rejected
 *       transfer leaves no entry.
 * </ul>
 *
 * <p>This is a regression guard, proven by experiment: with {@code @Version} removed, ~85% of the cache updates are
 * lost and these assertions go red. Note that <b>without retry</b> a large fraction of the transfers currently fail
 * (the optimistic-lock conflict surfaces as an error) — that liveness gap is LDG-52, which adds bounded retry and will
 * strengthen this test to "all {@value N} succeed". Correctness, asserted here, already holds.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TransferConcurrencyStressTest {

    private static final int N = 50;
    private static final long AMOUNT = 100;
    private static final long FUNDING = N * AMOUNT;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private long balanceCache(UUID id) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }

    /** Ledger truth for an account: CREDIT adds, DEBIT subtracts. */
    private long ledgerBalance(UUID id) {
        return jdbc.sql("SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)"
                        + " FROM ledger_entries WHERE account_id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }

    private long transferEntryCount(UUID id) {
        // entries on `id` excluding the single funding top-up CREDIT
        return jdbc.sql("SELECT count(*) FROM ledger_entries WHERE account_id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }

    @Test
    void concurrentTransfersFromOneAccountNeverCorruptTheBooks() throws Exception {
        UUID from = createAccount();
        UUID to = createAccount();
        mockMvc.perform(post("/accounts/{id}/topups", from)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":%d}".formatted(FUNDING)))
                .andExpect(status().isCreated());

        AtomicInteger successes = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    int code = mockMvc.perform(post("/transfers")
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinorUnits\":%d}"
                                            .formatted(from, to, AMOUNT)))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (code == 201) {
                        successes.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // counted as a non-success; the safety assertions below don't depend on the failure mode
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();

        int ok = successes.get();

        // (1) no lost update: cache == ledger truth for both accounts
        assertThat(balanceCache(from)).as("source cache == ledger").isEqualTo(ledgerBalance(from));
        assertThat(balanceCache(to)).as("dest cache == ledger").isEqualTo(ledgerBalance(to));

        // (2) no negative balance
        assertThat(balanceCache(from)).as("source never negative").isGreaterThanOrEqualTo(0);

        // (3) exact accounting: each 201 moved exactly AMOUNT, nothing lost or duplicated
        assertThat(balanceCache(from))
                .as("source balance reflects exactly the successful transfers")
                .isEqualTo(FUNDING - (long) ok * AMOUNT);
        assertThat(balanceCache(to))
                .as("dest balance reflects exactly the successful transfers")
                .isEqualTo((long) ok * AMOUNT);

        // (4) postings match successes: `to` has one CREDIT per successful transfer; `from` has the funding CREDIT +
        // one DEBIT each
        assertThat(transferEntryCount(to))
                .as("one CREDIT entry per successful transfer")
                .isEqualTo(ok);
        assertThat(transferEntryCount(from))
                .as("funding CREDIT + one DEBIT per successful transfer")
                .isEqualTo(ok + 1L);
    }
}
