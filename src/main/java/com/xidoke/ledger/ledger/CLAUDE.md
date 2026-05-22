# ledger package — CLAUDE.md

Owns the `Transaction` aggregate root — the double-entry posting where `Σ DEBIT == Σ CREDIT` is enforced (`post()`), plus `TransactionType`/`TransactionStatus` and the posting/reconciliation logic still to come (ADR-0005, ADR-0006, ADR-0016).

Note: the immutable `LedgerEntry` fact lives in `common/domain` (shared kernel), not here — it flows between the `Account` and `Transaction` aggregates, so it is shared rather than owned by one feature. See ADR-0010 references + vault DDD note `cross-aggregate-objects-and-the-ledger-entry-question`.

## Append-only enforcement (ledger_entries, ADR-0005)

Defence in depth across four layers — a posted entry is never updated or deleted; corrections are new reversing entries (`correcting-entry`):

1. **Domain** — `LedgerEntry` is an immutable `record` (no setters). ArchUnit `ledgerEntriesAreImmutable` guards it.
2. **Read port** — `LedgerEntryQueryRepository` is read-only (no save/update/delete). ArchUnit `ledgerEntryQueryRepositoryIsReadOnly` guards it.
3. **Write path** — the only entry writer is `TransactionPostingAdapter`, which INSERTs only.
4. **Database** — `ledger_entries_append_only` trigger (V4) raises on any `UPDATE`/`DELETE`. This is the hard guarantee, independent of app correctness; proven by `LedgerSchemaMigrationTest.ledgerEntriesAreAppendOnly`.

See repository root `CLAUDE.md` for project-wide conventions.
