package com.xidoke.ledger.transfer.adapter.in;

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
 * End-to-end transfer against a real Postgres (Testcontainers): a balanced DEBIT/CREDIT posting between two user
 * accounts in one transaction, plus the failure paths (insufficient funds rolls back fully; self-transfer, currency
 * mismatch, unknown account, and validation each map to the right ProblemDetail status).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TransferEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createAccount(String currency) throws Exception {
        String json = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerRef\":\"owner\",\"currency\":\"%s\"}".formatted(currency)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private void topup(UUID accountId, long amount) throws Exception {
        mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":%d}".formatted(amount)))
                .andExpect(status().isCreated());
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

    @Test
    void transferMovesMoneyAsBalancedPair() throws Exception {
        UUID from = createAccount("USD");
        UUID to = createAccount("USD");
        topup(from, 1000);

        String json = mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinorUnits\":300}"
                                .formatted(from, to)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromBalanceMinorUnits").value(700))
                .andExpect(jsonPath("$.toBalanceMinorUnits").value(300))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.transactionId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID txId =
                UUID.fromString(objectMapper.readTree(json).get("transactionId").asText());

        assertThat(balanceOf(from)).isEqualTo(700L);
        assertThat(balanceOf(to)).isEqualTo(300L);

        // exactly two entries for the posting: DEBIT on the source, CREDIT on the destination
        Long count = jdbc.sql("SELECT count(*) FROM ledger_entries WHERE transaction_id = :tx")
                .param("tx", txId)
                .query(Long.class)
                .single();
        assertThat(count).isEqualTo(2L);
        assertThat(directionFor(txId, from)).isEqualTo("DEBIT");
        assertThat(directionFor(txId, to)).isEqualTo("CREDIT");
    }

    private String directionFor(UUID txId, UUID accountId) {
        return jdbc.sql("SELECT direction FROM ledger_entries WHERE transaction_id = :tx AND account_id = :acc")
                .param("tx", txId)
                .param("acc", accountId)
                .query(String.class)
                .single();
    }

    @Test
    void insufficientFundsReturns422AndRollsBackFully() throws Exception {
        UUID from = createAccount("USD");
        UUID to = createAccount("USD");
        topup(from, 100);
        long fromEntriesBefore = entryCount(from);
        long toEntriesBefore = entryCount(to);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinorUnits\":101}"
                                .formatted(from, to)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // nothing moved: balances unchanged and no new ledger entries on either account
        assertThat(balanceOf(from)).isEqualTo(100L);
        assertThat(balanceOf(to)).isEqualTo(0L);
        assertThat(entryCount(from)).isEqualTo(fromEntriesBefore);
        assertThat(entryCount(to)).isEqualTo(toEntriesBefore);
    }

    @Test
    void selfTransferReturns422() throws Exception {
        UUID account = createAccount("USD");
        topup(account, 500);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinorUnits\":100}"
                                .formatted(account, account)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(balanceOf(account)).isEqualTo(500L);
    }

    @Test
    void currencyMismatchReturns422() throws Exception {
        UUID from = createAccount("USD");
        UUID to = createAccount("VND");
        topup(from, 1000);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinorUnits\":100}"
                                .formatted(from, to)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(balanceOf(from)).isEqualTo(1000L);
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        UUID to = createAccount("USD");

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinorUnits\":100}"
                                .formatted(UUID.randomUUID(), to)))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonPositiveAmountReturns400() throws Exception {
        UUID from = createAccount("USD");
        UUID to = createAccount("USD");

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinorUnits\":0}"
                                .formatted(from, to)))
                .andExpect(status().isBadRequest());
    }
}
