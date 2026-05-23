package com.xidoke.ledger.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xidoke.ledger.TestcontainersConfiguration;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.topup.application.TopupResult;
import com.xidoke.ledger.topup.application.TopupService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
 * Transactional-outbox write side (LDG-54, ADR-0013): a posting appends exactly one PENDING event, and — the point of
 * the pattern — that event row lives in the <b>same</b> transaction as the ledger write, so a rolled-back posting
 * leaves neither (no dual-write).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ledger.outbox.initial-delay=PT1H") // keep the poller from draining mid-assert
class OutboxWriteTest {

    @Autowired
    private TopupService topupService;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void successfulTopupAppendsOnePendingEvent() throws Exception {
        UUID account = createAccount();

        TopupResult result = topupService.topup(AccountId.of(account), 500);
        UUID txId = result.transactionId().value();

        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE aggregate_id = :id")
                        .param("id", txId)
                        .query(Long.class)
                        .single())
                .as("exactly one event appended")
                .isEqualTo(1L);
        assertThat(scalar("SELECT event_type FROM outbox WHERE aggregate_id = :id", txId))
                .isEqualTo("TopupPosted");
        assertThat(scalar("SELECT status FROM outbox WHERE aggregate_id = :id", txId))
                .isEqualTo("PENDING");
        assertThat(scalar("SELECT payload->>'amountMinorUnits' FROM outbox WHERE aggregate_id = :id", txId))
                .isEqualTo("500");
        assertThat(scalar("SELECT payload->>'accountId' FROM outbox WHERE aggregate_id = :id", txId))
                .isEqualTo(account.toString());
    }

    @Test
    void rolledBackPostingLeavesNoOutboxRowOrLedgerEntry() throws Exception {
        UUID account = createAccount();
        long outboxBefore = count("SELECT count(*) FROM outbox");
        long entriesBefore = count("SELECT count(*) FROM ledger_entries");

        TransactionTemplate tt = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tt.executeWithoutResult(status -> {
                    topupService.topup(AccountId.of(account), 500);
                    throw new IllegalStateException("force rollback after the posting + outbox append");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("SELECT count(*) FROM outbox"))
                .as("outbox row rolled back with the posting")
                .isEqualTo(outboxBefore);
        assertThat(count("SELECT count(*) FROM ledger_entries"))
                .as("ledger entries rolled back too — same transaction")
                .isEqualTo(entriesBefore);
    }

    private String scalar(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(String.class).single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
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
