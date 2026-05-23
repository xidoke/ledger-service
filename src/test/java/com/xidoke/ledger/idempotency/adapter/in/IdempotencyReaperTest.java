package com.xidoke.ledger.idempotency.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.TestcontainersConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the {@link IdempotencyKeyReaper} sweep (LDG-67, ADR-0012): a PENDING claim orphaned by a crash (older than
 * the in-flight window) is reaped so the key stops 409-ing forever, while a fresh PENDING claim is left alone.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyReaperTest {

    @Autowired
    private IdempotencyKeyReaper reaper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sweepReapsStaleRowsButKeepsFreshOnes() {
        String stalePending = insertPending(Instant.now().minus(1, ChronoUnit.HOURS)); // past PT10M window
        String freshPending = insertPending(Instant.now());
        String oldCompleted = insertCompleted(Instant.now().minus(25, ChronoUnit.HOURS)); // past PT24H window
        String recentCompleted = insertCompleted(Instant.now().minus(1, ChronoUnit.HOURS));

        reaper.sweep();

        assertThat(exists(stalePending)).as("orphaned PENDING reaped").isFalse();
        assertThat(exists(oldCompleted))
                .as("COMPLETED past retry window reaped")
                .isFalse();
        assertThat(exists(freshPending)).as("in-window PENDING kept").isTrue();
        assertThat(exists(recentCompleted)).as("in-window COMPLETED kept").isTrue();
    }

    @Test
    void aSweptKeyIsReusableInsteadOfStuckOn409() throws Exception {
        UUID account = createAccount();
        String key = UUID.randomUUID().toString();
        insertPendingWithKey(key, Instant.now().minus(1, ChronoUnit.HOURS)); // orphaned claim

        reaper.sweep();
        assertThat(exists(key)).as("orphaned claim reaped").isFalse();

        // the same key is now free, so a real request goes through (201) rather than hitting the stuck 409
        mockMvc.perform(post("/accounts/{id}/topups", account)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":500}"))
                .andExpect(status().isCreated());
    }

    private String insertPending(Instant createdAt) {
        return insertPendingWithKey(UUID.randomUUID().toString(), createdAt);
    }

    private String insertPendingWithKey(String key, Instant createdAt) {
        jdbc.sql("INSERT INTO idempotency_keys (key, request_hash, status, created_at) "
                        + "VALUES (:k, :h, 'PENDING', :ts)")
                .param("k", key)
                .param("h", "0".repeat(64))
                .param("ts", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
        return key;
    }

    private String insertCompleted(Instant createdAt) {
        String key = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO idempotency_keys (key, request_hash, status, response_status, response_body, created_at) "
                        + "VALUES (:k, :h, 'COMPLETED', 201, '{}', :ts)")
                .param("k", key)
                .param("h", "0".repeat(64))
                .param("ts", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
        return key;
    }

    private boolean exists(String key) {
        return jdbc.sql("SELECT count(*) FROM idempotency_keys WHERE key = :k")
                        .param("k", key)
                        .query(Long.class)
                        .single()
                > 0;
    }

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
}
