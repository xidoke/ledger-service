# ledger package — CLAUDE.md

Owns the `Transaction` aggregate root — the double-entry posting where `Σ DEBIT == Σ CREDIT` is enforced (`post()`), plus `TransactionType`/`TransactionStatus` and the posting/reconciliation logic still to come (ADR-0005, ADR-0006, ADR-0016).

Note: the immutable `LedgerEntry` fact lives in `common/domain` (shared kernel), not here — it flows between the `Account` and `Transaction` aggregates, so it is shared rather than owned by one feature. See ADR-0010 references + vault DDD note `cross-aggregate-objects-and-the-ledger-entry-question`.

See repository root `CLAUDE.md` for project-wide conventions.
