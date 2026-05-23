package com.xidoke.ledger.idempotency.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the idempotency guarantee under real concurrency (Testcontainers Postgres): two requests carrying the same
 * {@code Idempotency-Key} are fired from two threads released together. The claim-first {@code INSERT … ON CONFLICT DO
 * NOTHING} serializes them at the DB, so the side effect runs <b>exactly once</b> regardless of timing — the loser
 * either replays (if the winner already finished) or gets 409 (in flight). We assert the exactly-once guarantee, not
 * "identical response", because the chosen in-flight behaviour is fail-fast 409, not block-and-replay (ADR-0012).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyConcurrencyTest {

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

    private long balanceOf(UUID accountId) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(Long.class)
                .single();
    }

    private long entryCount(UUID accountId) {
        return jdbc.sql("SELECT count(*) FROM ledger_entries WHERE account_id = :id")
                .param("id", accountId)
                .query(Long.class)
                .single();
    }

    /** Fires two same-key top-ups from two threads released simultaneously; returns the two HTTP statuses. */
    private List<Integer> fireConcurrently(UUID accountId, String key, String bodyA, String bodyB) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> a = topupTask(accountId, key, bodyA, start);
            Callable<Integer> b = topupTask(accountId, key, bodyB, start);
            Future<Integer> fa = pool.submit(a);
            Future<Integer> fb = pool.submit(b);
            start.countDown(); // release both at once
            return List.of(fa.get(), fb.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> topupTask(UUID accountId, String key, String body, CountDownLatch start) {
        return () -> {
            start.await();
            return mockMvc.perform(post("/accounts/{id}/topups", accountId)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }

    @Test
    void concurrentSameKeySameBodyAppliesExactlyOnce() throws Exception {
        UUID accountId = createAccount();
        String key = UUID.randomUUID().toString();
        String body = "{\"amountMinorUnits\":1000}";

        List<Integer> statuses = fireConcurrently(accountId, key, body, body);

        // exactly-once: balance moved by a single top-up; one CREDIT entry on the account (not two)
        assertThat(balanceOf(accountId)).isEqualTo(1000L);
        assertThat(entryCount(accountId)).isEqualTo(1L);
        // the winner is 201; the loser either replays (201) or is rejected in-flight (409) — never a fresh second post
        assertThat(statuses).contains(201);
        assertThat(statuses).allSatisfy(s -> assertThat(s).isIn(201, 409));
    }

    @Test
    void concurrentSameKeyDifferentBodyAppliesExactlyOnce() throws Exception {
        UUID accountId = createAccount();
        String key = UUID.randomUUID().toString();

        List<Integer> statuses =
                fireConcurrently(accountId, key, "{\"amountMinorUnits\":1000}", "{\"amountMinorUnits\":2000}");

        // exactly one of the two amounts was applied; never both
        assertThat(balanceOf(accountId)).isIn(1000L, 2000L);
        assertThat(entryCount(accountId)).isEqualTo(1L);
        // one winner (201), one conflict: 409 (winner still in flight) or 422 (winner done, hash differs)
        assertThat(statuses).contains(201);
        assertThat(statuses).allSatisfy(s -> assertThat(s).isIn(201, 409, 422));
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
    }
}
