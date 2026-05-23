package com.xidoke.ledger.idempotency.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.TestcontainersConfiguration;
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
 * End-to-end idempotency filter against a real Postgres (Testcontainers): a top-up carrying an {@code Idempotency-Key}
 * is replayed (not re-executed) on retry; a key reused with a different body is rejected 422; and a request without the
 * header runs normally each time.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyKeyEndpointTest {

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

    @Test
    void sameKeyAndBodyReplaysWithoutReExecuting() throws Exception {
        UUID accountId = createAccount();
        String key = UUID.randomUUID().toString();
        String body = "{\"amountMinorUnits\":1000}";

        String first = mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String replayed = mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // identical response body, and the side effect ran exactly once (balance == 1000, not 2000)
        assertThat(replayed).isEqualTo(first);
        assertThat(balanceOf(accountId)).isEqualTo(1000L);
    }

    @Test
    void sameKeyDifferentBodyReturns422() throws Exception {
        UUID accountId = createAccount();
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":1000}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":2000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // the conflicting second request never ran: balance is still the first top-up only
        assertThat(balanceOf(accountId)).isEqualTo(1000L);
    }

    @Test
    void withoutKeyEachRequestRunsNormally() throws Exception {
        UUID accountId = createAccount();
        String body = "{\"amountMinorUnits\":1000}";

        mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // no idempotency key → both top-ups applied
        assertThat(balanceOf(accountId)).isEqualTo(2000L);
    }

    @Test
    void replayedResponseEchoesStoredTransactionId() throws Exception {
        UUID accountId = createAccount();
        String key = UUID.randomUUID().toString();
        String body = "{\"amountMinorUnits\":500}";

        String first = mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String txId = objectMapper.readTree(first).get("transactionId").asText();

        mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(txId));
    }
}
