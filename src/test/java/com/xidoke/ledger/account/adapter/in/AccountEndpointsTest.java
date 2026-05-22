package com.xidoke.ledger.account.adapter.in;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** End-to-end web slice for the account endpoints against a real Postgres (Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createAccount(String ownerRef, String currency) throws Exception {
        String json = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerRef\":\"%s\",\"currency\":\"%s\"}".formatted(ownerRef, currency)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.currency").value(currency))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balanceMinorUnits").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    @Test
    void createReturns201WithBody() throws Exception {
        createAccount("owner-1", "USD");
    }

    @Test
    void getReturnsAccountDetail() throws Exception {
        UUID id = createAccount("owner-2", "USD");
        mockMvc.perform(get("/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.ownerRef").value("owner-2"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void unknownAccountReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void invalidCreateReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currency").exists());
    }

    @Test
    void entriesReturnsAccountHistory() throws Exception {
        UUID accountId = createAccount("owner-3", "USD");
        UUID txId = UUID.randomUUID();
        jdbc.sql("INSERT INTO transactions (id, type, status) VALUES (:id, 'TRANSFER', 'POSTED')")
                .param("id", txId)
                .update();
        jdbc.sql("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount) "
                        + "VALUES (:tx, :acc, 'DEBIT', 100)")
                .param("tx", txId)
                .param("acc", accountId)
                .update();
        jdbc.sql("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount) "
                        + "VALUES (:tx, :acc, 'CREDIT', 250)")
                .param("tx", txId)
                .param("acc", accountId)
                .update();

        mockMvc.perform(get("/accounts/{id}/entries", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].currency", everyItem(is("USD"))))
                .andExpect(jsonPath("$[*].transactionId", everyItem(is(txId.toString()))))
                .andExpect(jsonPath("$[*].amountMinorUnits", containsInAnyOrder(100, 250)));
    }

    @Test
    void entriesIsEmptyForFreshAccount() throws Exception {
        UUID accountId = createAccount("owner-4", "USD");
        mockMvc.perform(get("/accounts/{id}/entries", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
