# Concurrency model

How concurrent writes to the same account stay correct, and why optimistic locking + retry was chosen. Expands the [country map](../../ARCHITECTURE.md) §Module map (`account/`). Full rationale + numbers: ADR-0011.

**Diátaxis**: Explanation. See ADR-0010 (Account = locking boundary), ADR-0006 (balance cache), ADR-0012 (idempotency composition).

## The race it prevents

Two requests debit the same account concurrently:

```
R1: read balance $500 (enough)      R2: read balance $500 (enough)
R1: commit −$300 → $200             R2: commit −$400 → −$200   ← overdraft / lost update
```

`READ COMMITTED` (PostgreSQL default) does not stop this read-modify-write race.

## The mechanism

- **Optimistic lock**: `accounts.version` (`@Version`). Hibernate writes `UPDATE … SET balance=?, version=version+1 WHERE id=? AND version=?`; the loser (0 rows) gets `OptimisticLockingFailureException` at commit — detection, not blocking. Reads never block (the ledger is read-heavy).
- **Bounded retry** (`common/concurrency/OptimisticRetry`, *outside* `@Transactional`): each attempt runs a fresh transaction that reloads the row at its current version, with capped attempts + exponential backoff + full jitter (never an unbounded spin). Exhaustion → `409`, not `500`.
- **Determinism**: the k-th committer can only lose to a distinct earlier committer, so it needs ≤ k attempts — moderate contention resolves fully (no flaky tests); a genuine hot account surfaces as `409`.
- **Composes with idempotency** (ADR-0012): retry sits after the `Idempotency-Key` is claimed, so a retry is transparent to the client and never double-posts.

## Why optimistic, not pessimistic (measured)

Benchmark (`TransferConcurrencyBenchmark`, 50 writers, one Postgres):

|              Scenario              |    Optimistic + retry     | Pessimistic `FOR UPDATE` |
|------------------------------------|---------------------------|--------------------------|
| Low-contention (50 disjoint pairs) | **34 ms** · 0 retry waste | 31 ms                    |
| High-contention (50 → one hot row) | 731 ms · 185 retry waste  | 358 ms                   |

Low contention (the norm) is a tie and optimistic **never blocks reads** → keep optimistic. A single hot row favours pessimistic ~2×, but pessimistic serializes + blocks reads and does **not** solve a true hot account — the answer there is async/sharding, not pessimistic-everywhere. The 185 wasted retries quantify the threshold at which a hot account (e.g. every top-up debiting `SYSTEM_FUNDING`) needs that escalation. See ADR-0011 and `Research/ledger-systems/wiki/system-funding-account-design.md`.
