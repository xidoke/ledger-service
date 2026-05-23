package com.xidoke.ledger.ledger.adapter.in;

import com.xidoke.ledger.ledger.domain.BalanceDrift;
import com.xidoke.ledger.ledger.domain.ReconciliationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic reconciliation safety net (ADR-0016). Re-derives each account's balance from the immutable ledger and checks
 * it against the cached {@code accounts.balance} (ADR-0006), plus the system-wide trial balance. On drift it logs an
 * ERROR with both values and bumps a metric — it never auto-corrects, because a silent fix would hide the bug that
 * caused the drift; an operator investigates and, if needed, posts a correcting entry. Idempotent (read-only), so it
 * can run any time.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationRepository reconciliation;
    private final AtomicInteger driftGauge = new AtomicInteger(0);

    public ReconciliationJob(ReconciliationRepository reconciliation, MeterRegistry meterRegistry) {
        this.reconciliation = reconciliation;
        meterRegistry.gauge("ledger.reconciliation.balance_drift_accounts", driftGauge);
    }

    /** Returns the number of drifting accounts found (also published as the gauge). */
    @Scheduled(cron = "${ledger.reconciliation.cron:0 0 2 * * *}")
    @Transactional(readOnly = true)
    public int reconcile() {
        List<BalanceDrift> drift = reconciliation.findBalanceDrift();
        long imbalance = reconciliation.trialBalanceImbalance();
        driftGauge.set(drift.size());

        for (BalanceDrift d : drift) {
            log.error(
                    "Balance drift: account={} cached={} ledger={} drift={} — investigate, do not auto-correct",
                    d.accountId(),
                    d.cachedBalance(),
                    d.ledgerBalance(),
                    d.drift());
        }
        if (imbalance != 0) {
            log.error("Trial-balance broken: Σdebit − Σcredit = {} (must be 0 — money created/destroyed)", imbalance);
        }
        if (drift.isEmpty() && imbalance == 0) {
            log.info("Reconciliation OK — every cached balance matches the ledger and the trial balance is zero");
        }
        return drift.size();
    }
}
