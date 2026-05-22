# Glossary

**Source of truth for domain terms. When in doubt, this file wins.**

This is the **ubiquitous language** of the ledger service: the canonical name for
each domain concept. Use these exact names in code (classes, methods, constants),
commit messages, and docs. If code and this file disagree, open a PR to reconcile
them — don't let the meanings drift apart.

**Diátaxis**: Reference.

The **Ref** column points to the decision or code that pins a term. ADRs `0001`–`0007`
live in [docs/adr/](adr/); ADRs `0008`+ are written in Phase 1, so terms that depend
on them are marked _(Phase 1)_.

## Accounting & ledger core

|             Term             |                                                                                    Definition                                                                                    |                      Ref                       |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| **Account**                  | One wallet. Has `id`, `owner_ref`, `currency`, `status`, `balance` (cache), `version` (optimistic-lock).                                                                         | `account/` package                             |
| **Accounting equation**      | `Assets = Liabilities + Equity`. Here, simplified: the sum of system (funding) account balances plus user account balances is constant.                                          | —                                              |
| **Append-only**              | A data structure that only accepts `INSERT` — never `UPDATE`/`DELETE`. `ledger_entries` is append-only: the immutable source of truth.                                           | [ADR-0005](adr/0005-ledger-model.md)           |
| **Audit trail**              | A forever-traceable history of changes. The ledger has one for free because entries are append-only.                                                                             | [ADR-0001](adr/0001-architectural-style.md)    |
| **Balance**                  | The amount in an account at a point in time. Here it is a **cache** (`accounts.balance`) updated in the same transaction as the entries; the **source of truth** is `Σ entries`. | [ADR-0006](adr/0006-balance-representation.md) |
| **Double-entry bookkeeping** | The accounting principle that every transaction posts ≥ 2 entries with `Σ DEBIT == Σ CREDIT`.                                                                                    | [ADR-0005](adr/0005-ledger-model.md)           |
| **Ledger**                   | The append-only journal of entries — the `ledger_entries` table.                                                                                                                 | `ledger/` package                              |
| **LedgerEntry**              | A single DEBIT or CREDIT line belonging to one transaction. Immutable.                                                                                                           | [ADR-0005](adr/0005-ledger-model.md)           |
| **Posting**                  | The act of writing a balanced set of entries (debit + credit) for one transaction. An atomic operation.                                                                          | [ADR-0005](adr/0005-ledger-model.md)           |
| **Projection**               | A view derived from source events. Balance is a projection of entries.                                                                                                           | [ADR-0006](adr/0006-balance-representation.md) |
| **Transaction (domain)**     | One ledger operation that groups 2+ entries (the `transactions` table). **Not** a DB transaction — see the disambiguation table.                                                 | [ADR-0005](adr/0005-ledger-model.md)           |
| **Correcting entry**         | To fix a wrong entry you never update it — you post a new reversing entry to cancel it, then the correct one.                                                                    | [ADR-0005](adr/0005-ledger-model.md)           |

## Credit & debit (project convention)

|    Term    |                                                 Definition                                                  |                 Ref                  |
|------------|-------------------------------------------------------------------------------------------------------------|--------------------------------------|
| **CREDIT** | An entry direction. Project convention: "money in" from the account's point of view. **Not** a credit card. | [ADR-0005](adr/0005-ledger-model.md) |
| **DEBIT**  | An entry direction. Project convention: "money out" from the account's point of view.                       | [ADR-0005](adr/0005-ledger-model.md) |

> The exact debit/credit direction convention (which sign increases a wallet
> balance) is fixed in [ADR-0005](adr/0005-ledger-model.md).

## Money

|      Term       |                                                                        Definition                                                                        |                     Ref                      |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| **Minor units** | Money stored as an integer in the currency's smallest unit (USD cents = 1/100; VND has no subunit, so the value is itself). Avoids floating-point error. | [ADR-0007](adr/0007-money-representation.md) |
| **Money**       | An immutable value: an amount in minor units plus a currency. Compared by value, never by `==` on a boxed type.                                          | [ADR-0007](adr/0007-money-representation.md) |

## Concurrency & integrity

|          Term           |                                                      Definition                                                       |                               Ref                               |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| **Aggregate**           | DDD term — a consistency boundary. Here one `Account` is one aggregate (one locking boundary).                        | [ADR-0004](adr/0004-package-structure.md), ADR-0010 _(Phase 1)_ |
| **Invariant**           | A property that must always hold. For the ledger: `Σ DEBIT == Σ CREDIT`, `balance == Σ entries`, entries append-only. | [ADR-0005](adr/0005-ledger-model.md)                            |
| **Optimistic locking**  | A concurrency strategy: don't lock the row; check a `version` before `UPDATE`, retry on conflict.                     | ADR-0011 _(Phase 1)_                                            |
| **`version` column**    | A `BIGINT` for optimistic locking; JPA `@Version` adds `WHERE version = :expected` to the `UPDATE`.                   | ADR-0011 _(Phase 1)_                                            |
| **Pessimistic locking** | Locking the row with `SELECT … FOR UPDATE`, blocking other writers. The non-default strategy here.                    | [ADR-0003](adr/0003-database.md)                                |
| **Lost update**         | A race where two transactions read the same value, both write, and one write is silently overwritten.                 | ADR-0011 _(Phase 1)_                                            |
| **Hot account**         | An account with many concurrent operations (e.g. the system funding account) — high contention.                       | ADR-0011 _(Phase 1)_                                            |
| **Retry**               | Re-running an operation (with backoff) after a recoverable failure such as an optimistic-lock conflict.               | ADR-0011 _(Phase 1)_                                            |
| **MVCC**                | Multi-Version Concurrency Control — PostgreSQL's mechanism where readers see a snapshot and don't block writers.      | [ADR-0003](adr/0003-database.md)                                |
| **Isolation level**     | PostgreSQL concurrency setting: `READ COMMITTED` / `REPEATABLE READ` / `SERIALIZABLE`.                                | [ADR-0003](adr/0003-database.md)                                |

## Events, outbox & idempotency

|           Term           |                                                                                       Definition                                                                                       |                     Ref                     |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| **Idempotency**          | The property that performing an operation N times equals performing it once. For an API: retry-safe.                                                                                   | ADR-0012 _(Phase 1)_                        |
| **Idempotency key**      | A client-generated UUID identifying one logical operation, sent via the `Idempotency-Key` header (Stripe convention).                                                                  | ADR-0012 _(Phase 1)_                        |
| **Outbox**               | A pattern: insert an event row in the same transaction as the business write; a separate poller publishes it.                                                                          | ADR-0013 _(Phase 1)_                        |
| **Outbox poller**        | A scheduled job that reads `outbox WHERE status = PENDING`, publishes the event, and marks it `SENT`.                                                                                  | ADR-0013 _(Phase 1)_                        |
| **Transactional outbox** | The same as Outbox — the full pattern name.                                                                                                                                            | ADR-0013 _(Phase 1)_                        |
| **Schema versioning**    | Every event payload carries a `schema_version` field from day one, so later evolution never needs a hard migration.                                                                    | ADR-0015 _(Phase 1)_                        |
| **Effectively-once**     | At-least-once delivery + an idempotent consumer = looks like exactly-once at the processing layer.                                                                                     | ADR-0013 _(Phase 1)_                        |
| **Event sourcing**       | Storing a sequence of events as the truth and deriving state by replay. Explicitly **not** the model here (see [ADR-0001](adr/0001-architectural-style.md)); a much later possibility. | [ADR-0001](adr/0001-architectural-style.md) |

## Operations & API

|        Term         |                                                            Definition                                                            |         Ref          |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------|----------------------|
| **Funding account** | A special account so that a top-up still balances double-entry: top-up = DEBIT funding, CREDIT user. Its balance stays negative. | ADR-0009 _(Phase 1)_ |
| **Topup**           | Adding money to an account: DEBIT funding, CREDIT user.                                                                          | ADR-0009 _(Phase 1)_ |
| **Reconciliation**  | A periodic job that checks `Σ entries == account.balance` and alerts on drift. A sanity check, not a fixer.                      | ADR-0016 _(Phase 1)_ |
| **ProblemDetail**   | RFC 7807 — the standard JSON shape for an HTTP error response. Built into Spring Boot 3.                                         | ADR-0017 _(Phase 1)_ |
| **Saga**            | A distributed-transaction pattern: a sequence of local transactions plus compensating transactions when a leg fails.             | _(Phase 2)_          |
| **Snapshot**        | A cached aggregate state used to rebuild a projection without replaying full history.                                            | _(later phase)_      |

## Naming disambiguation (read this)

The "not to be confused with" column prevents most of the misunderstandings in a
financial codebase.

|                 In this project                  |                                    Not to be confused with                                    |
|--------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `Transaction` (domain — a posting of 2+ entries) | DB transaction (`@Transactional`, `BEGIN … COMMIT`) — say "DB transaction" when you mean that |
| `Account` (a wallet / ledger account)            | A user/auth account (there is no user auth in Phase 0–1)                                      |
| `Balance` (the same-transaction cache)           | "Account balance" in an audit context, which means the projection `Σ entries`                 |
| `LedgerEntry` (a domain debit/credit line)       | "Entry point" (a technical term) — say "ledger entry" when specific                           |
| `Event` (an outbox event row)                    | An event-sourcing event store record — the outbox event is not that                           |
| `LDG-N`                                          | A Linear issue identifier (workspace key `LDG`)                                               |

## References

- Architecture decisions — [docs/adr/](adr/) (`0001`–`0007` today; `0008`+ in Phase 1)
- Module map — [ARCHITECTURE.md](../ARCHITECTURE.md)
- Update this file whenever a domain term is added, renamed, or clarified — same PR as the code change.
