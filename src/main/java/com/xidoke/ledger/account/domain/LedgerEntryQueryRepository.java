package com.xidoke.ledger.account.domain;

import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.LedgerEntry;
import java.util.List;

/**
 * Read-only query port for an account's ledger-entry history (a small slice of CQRS — the write path goes through the
 * posting aggregate, ADR-0019). {@code ledger_entries} stores no currency (single-currency, ADR-0008), so the account's
 * currency is supplied to reconstruct {@code Money}.
 */
public interface LedgerEntryQueryRepository {

    List<LedgerEntry> findByAccount(AccountId accountId, String currencyCode);
}
