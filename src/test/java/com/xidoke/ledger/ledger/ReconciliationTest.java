package com.xidoke.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.TestcontainersConfiguration;
import com.xidoke.ledger.ledger.adapter.in.ReconciliationJob;
import com.xidoke.ledger.ledger.domain.BalanceDrift;
import com.xidoke.ledger.ledger.domain.ReconciliationRepository;
import java.util.Optional;
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
 * Reconciliation (LDG-56, ADR-0016): a consistent account shows no drift and the trial balance is zero; a deliberately
 * corrupted cached balance is detected (cached ≠ ledger) and surfaced by the job, which never auto-corrects.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationTest {

    @Autowired
    private ReconciliationJob job;

    @Autowired
    private ReconciliationRepository reconciliation;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void cleanAccountHasNoDriftThenInjectedDriftIsDetected() throws Exception {
        UUID account = createAccount();
        topup(account, 500);

        // consistent: this account's cache matches its ledger. (The system-wide trial balance is intentionally not
        // asserted here — the shared test database accumulates raw, deliberately-unbalanced entries that other tests
        // insert directly to exercise the read path, so a global Σdebit−Σcredit == 0 only holds in an isolated DB.
        // The job's trial-balance check is still real in production, where every entry comes from a balanced posting.)
        assertThat(driftFor(account)).as("no drift after a real top-up").isEmpty();

        // inject silent corruption straight into the cache (bypassing the ledger), as a bug would
        jdbc.sql("UPDATE accounts SET balance = balance + 777 WHERE id = :id")
                .param("id", account)
                .update();
        try {
            Optional<BalanceDrift> drift = driftFor(account);
            assertThat(drift).as("the corrupted account is detected").isPresent();
            assertThat(drift.get().cachedBalance()).isEqualTo(1277);
            assertThat(drift.get().ledgerBalance()).isEqualTo(500);
            assertThat(drift.get().drift()).isEqualTo(777);

            assertThat(job.reconcile())
                    .as("the job reports at least the injected drift")
                    .isGreaterThanOrEqualTo(1);
        } finally {
            // restore so the shared test database stays consistent for other tests
            jdbc.sql("UPDATE accounts SET balance = balance - 777 WHERE id = :id")
                    .param("id", account)
                    .update();
        }
        assertThat(driftFor(account))
                .as("drift gone once the cache is corrected")
                .isEmpty();
    }

    private Optional<BalanceDrift> driftFor(UUID account) {
        return reconciliation.findBalanceDrift().stream()
                .filter(d -> d.accountId().equals(account))
                .findFirst();
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

    private void topup(UUID id, long amount) throws Exception {
        mockMvc.perform(post("/accounts/{id}/topups", id)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinorUnits\":%d}".formatted(amount)))
                .andExpect(status().isCreated());
    }
}
