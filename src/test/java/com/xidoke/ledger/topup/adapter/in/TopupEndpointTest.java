package com.xidoke.ledger.topup.adapter.in;

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

/** End-to-end top-up against a real Postgres (Testcontainers): one balanced posting against SYSTEM_FUNDING. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TopupEndpointTest {

    private static final UUID SYSTEM_FUNDING = UUID.fromString("00000000-0000-0000-0000-000000000001");

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

    private long balanceOf(UUID accountId) {
        return jdbc.sql("SELECT balance FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(Long.class)
                .single();
    }

    @Test
    void topupCreditsAccountAndPostsBalancedPair() throws Exception {
        UUID accountId = createAccount("USD");
        long fundingBefore = balanceOf(SYSTEM_FUNDING);

        String json = mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.balanceMinorUnits").value(1000))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.transactionId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID txId =
                UUID.fromString(objectMapper.readTree(json).get("transactionId").asText());

        // user wallet credited; SYSTEM_FUNDING debited by the same amount (delta — the row is shared across tests)
        assertThat(balanceOf(accountId)).isEqualTo(1000L);
        assertThat(balanceOf(SYSTEM_FUNDING)).isEqualTo(fundingBefore - 1000L);

        // exactly two entries for this posting: CREDIT on the user, DEBIT on SYSTEM_FUNDING
        Long entryCount = jdbc.sql("SELECT count(*) FROM ledger_entries WHERE transaction_id = :tx")
                .param("tx", txId)
                .query(Long.class)
                .single();
        assertThat(entryCount).isEqualTo(2L);
        assertThat(directionFor(txId, accountId)).isEqualTo("CREDIT");
        assertThat(directionFor(txId, SYSTEM_FUNDING)).isEqualTo("DEBIT");
    }

    private String directionFor(UUID txId, UUID accountId) {
        return jdbc.sql("SELECT direction FROM ledger_entries WHERE transaction_id = :tx AND account_id = :acc")
                .param("tx", txId)
                .param("acc", accountId)
                .query(String.class)
                .single();
    }

    @Test
    void topupUnknownAccountReturns404() throws Exception {
        mockMvc.perform(post("/accounts/{id}/topups", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":1000}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void topupNonPositiveAmountReturns400() throws Exception {
        UUID accountId = createAccount("USD");
        mockMvc.perform(post("/accounts/{id}/topups", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":0}"))
                .andExpect(status().isBadRequest());
    }
}
