---
title: "The race condition a stress test found in my double-entry ledger — and how I fixed it"
published: false
description: "A concurrency bug that lost 85% of balance updates under load, and the optimistic-locking + bounded-retry + idempotency stack that fixed it — with the benchmark numbers behind the choice."
tags: java, springboot, postgres, concurrency
canonical_url: https://github.com/xidoke/ledger-service/blob/main/docs/blog/01-double-entry-ledger-race-condition.md
---

I'm building [ledger-service](https://github.com/xidoke/ledger-service), a double-entry e-wallet ledger in Java 21 / Spring Boot 3.5 / PostgreSQL. It's [live on Render](https://ledger-service-bjzr.onrender.com). Early on I wrote a stress test that fires 50 transfers at the **same account** at once and asserts the books are never corrupted. It went red — and the way it went red is the most useful thing I've learned building this.

This post walks the whole chain: why a money ledger keeps a balance *cache* at all, the read-modify-write race that cache invites, how I detected it, the fix (optimistic locking + bounded retry), the benchmark that justified choosing optimistic over pessimistic locking, and how idempotency has to compose with retry so a network hiccup never double-spends.

## The setup: a ledger, and why it has a cache

The source of truth is a **double-entry, append-only** table (ADR-0005). Every money operation writes at least one balanced `DEBIT`/`CREDIT` pair where `Σ DEBIT == Σ CREDIT`, and `ledger_entries` is insert-only — no `UPDATE`, no `DELETE`. A mistake is fixed by posting a *correcting* entry, never by editing history. This is the model Stripe, Modern Treasury, and Formance all use, and it's what gives you an audit trail you can trust.

But "what is account X's balance?" should not be a `SUM` over every entry that account has ever had. So I keep a **cache**: `accounts.balance` is a materialized `Σ` of that account's entries, updated *in the same transaction* as the entries themselves (ADR-0006). The entries are the truth; the balance is a derived read cache that stays O(1).

That cache is exactly where concurrency bites.

## The race

Two requests debit the same account at the same time:

```
R1: read balance $500 (enough)      R2: read balance $500 (enough)
R1: commit −$300 → $200             R2: commit −$400 → −$200   ← overdraft / lost update
```

Both read `$500`, both decide they have enough, both write back their own idea of the new balance. One write silently clobbers the other: a **lost update**, and a balance that no longer matches the ledger entries underneath it.

The trap is assuming the database stops this for you. It does not. PostgreSQL's default isolation level, `READ COMMITTED`, only guarantees you don't read *uncommitted* data — it does nothing about two transactions that each read-then-write the same row concurrently. A read-modify-write race sails right through it.

## Detecting it: the stress test

Here's the test that surfaced the bug. Fund one account, then fire `N = 50` transfers out of it concurrently and check the books afterward:

```java
AtomicInteger successes = new AtomicInteger();
CountDownLatch start = new CountDownLatch(1);
ExecutorService pool = Executors.newFixedThreadPool(16);
for (int i = 0; i < N; i++) {
    pool.submit(() -> {
        start.await();                       // line them all up...
        int code = post("/transfers", from, to, AMOUNT);   // ...then fire at once
        if (code == 201) successes.incrementAndGet();
    });
}
start.countDown();
// after all complete:
assertThat(balanceCache(from)).isEqualTo(ledgerBalance(from));   // cache == Σ entries
assertThat(balanceCache(from)).isGreaterThanOrEqualTo(0);        // no overdraft
assertThat(balanceCache(to)).isEqualTo((long) ok * AMOUNT);      // exact accounting
```

The assertions are deliberately **timing-independent** — they hold for *any* split of successes and failures, because they compare the cache against the ledger truth rather than against a fixed expected count. That's what makes the test a stable regression guard instead of a flaky one.

I confirmed the bug by experiment: with the `@Version` column removed, **~85% of the cache updates were lost** and these assertions went red — the cached balance drifted far from the sum of the entries. The cache and the truth disagreed, which in a money system is the whole ballgame.

## The fix, part 1: optimistic locking

`accounts` already had a `version BIGINT` column, because the Account is the aggregate / locking boundary (ADR-0010). Mapping it as a JPA `@Version` turns every balance write into a compare-and-set:

```sql
UPDATE accounts SET balance = ?, version = version + 1
 WHERE id = ? AND version = ?
```

Two concurrent writers both load version `7`. The first to commit sets it to `8`. The second's `UPDATE ... WHERE version = 7` now matches **zero rows**, and Hibernate raises `OptimisticLockingFailureException` at commit time. The lost update is now impossible: instead of silently clobbering, the loser is *told* it lost.

The key property: this is **detection, not blocking**. No reader ever waits for a lock. For a ledger — where balance/history reads vastly outnumber writes — that matters a lot.

## The fix, part 2: bounded retry

Detection alone isn't enough. With `@Version` in place but no retry, the stress test stopped corrupting data but a big chunk of transfers now *failed* with a conflict — correct, but a lousy experience. So the loser needs to retry.

The retry helper sits **outside** `@Transactional`, and that placement is the whole point:

```java
public <T> T execute(Supplier<T> operation) {
    for (int attempt = 1; ; attempt++) {
        try {
            return operation.get();           // a FRESH transaction each attempt
        } catch (OptimisticLockingFailureException e) {
            if (attempt >= maxAttempts) throw new ConcurrencyConflictException();
            sleep(backoffWithFullJitter(attempt));   // 25–200 ms, capped
        }
    }
}
```

Each attempt is a brand-new transaction that **reloads the row at its current version** — retrying inside the failed transaction would just re-fail against the stale version. Defaults: 5 attempts, exponential backoff with **full jitter** (so a thundering herd doesn't resynchronize into another collision), and on exhaustion a clean `409 Conflict` — never a `500`.

There's a small piece of reasoning that makes this provably terminating under moderate load: **the k-th committer can only lose to a distinct *earlier* committer**, so it needs at most `k` attempts. With 4 concurrent writers and a 5-attempt budget, all 4 succeed deterministically — no flaky test. A genuine hot account (more concurrent writers than the attempt budget) surfaces as `409`, which is honest backpressure rather than a hidden corruption.

## Optimistic vs pessimistic: the measured choice

The obvious alternative is pessimistic locking — `SELECT ... FOR UPDATE` to lock the row before touching it, so writer #2 simply waits. No retries, easy to reason about. So why optimistic?

I didn't want to argue this from vibes, so I wrote a benchmark (`TransferConcurrencyBenchmark`) that runs the **identical transfer logic** under both strategies, 50 concurrent writers, against one real PostgreSQL:

|                      Scenario                      |            Optimistic + retry            | Pessimistic `FOR UPDATE` |
|----------------------------------------------------|------------------------------------------|--------------------------|
| **Low-contention** (50 disjoint account pairs)     | **34 ms** · 50/50 ok · **0 retry waste** | 31 ms · 50/50 ok         |
| **High-contention** (50 transfers → **1 hot row**) | 731 ms · 50/50 ok · **185 retry waste**  | **358 ms** · 50/50 ok    |

Reading the numbers:

- **Low contention is the common case, and it's a tie** (34 vs 31 ms) — but optimistic wastes *zero* retries and, crucially, **never blocks reads**. That's the deciding factor for a read-heavy ledger.
- **On a single hot row, pessimistic is ~2× faster** (358 vs 731 ms) and wastes nothing, while optimistic burns 185 extra attempts (≈4.7× the work) on collisions and backoff. But pessimistic "wins" here precisely by *serializing and blocking reads* — the thing I'm trying to avoid — and it doesn't actually *solve* a hot account, it just queues it.

So the verdict is optimistic + retry, and the value of the benchmark isn't "optimistic is faster" (it isn't, under contention) — it's that those **185 wasted retries quantify the threshold** at which a truly hot account (think: every top-up debiting a shared `SYSTEM_FUNDING` row) needs a real escalation: async queueing or sub-account sharding, not flipping the whole system to pessimistic locks.

## Composing with idempotency

There's one more way to double-spend that retry actually *makes worse* if you're not careful. A client whose connection drops after the server committed will retry the whole HTTP request — and now you risk posting the transfer twice. Retry-on-conflict and retry-on-network-blip are different problems, and the fix for one must not break the other.

So both money endpoints require an `Idempotency-Key` header (ADR-0012, the Stripe pattern). The mechanism that makes it concurrency-safe is **claim-first**:

```sql
INSERT INTO idempotency_keys (key, status) VALUES (?, 'PENDING')
ON CONFLICT (key) DO NOTHING;     -- committed immediately, before business logic
```

That atomic insert is the serialization point. Whoever wins the claim runs the operation; a concurrent request with the same key sees the committed `PENDING` row and gets `409` (in-flight) instead of running a second time. A completed key replays the stored response; a key reused with a *different* body gets `422` (a client contract violation, deliberately distinct from the `409` conflict code).

The reason this composes cleanly with the retry from earlier: the optimistic-lock retry sits **after** the key is claimed. All those internal attempts happen under one already-claimed idempotency key, so they're completely invisible to the client and can never produce a second posting. Conflict-retry and request-idempotency stack instead of fighting.

## What I'd reach for next

The cache is fast but it *can* drift (a bug, a partial failure). So a scheduled **reconciliation** job re-derives every balance from the immutable entries and alerts on any mismatch — it never auto-corrects; an operator posts a correcting entry. The append-only ledger means the truth is always recoverable.

And the hot-account ceiling those 185 retries exposed is the next real scaling problem: when one row is genuinely contended, the answer is async posting or sharding that account, with the retry rate as the signal that tells you when you've crossed the line.

---

The throughline: in a money system, *the cache disagreeing with the ledger* is the failure that matters, and a default-isolation database won't stop you from creating it. A `@Version` compare-and-set makes the lost update impossible, bounded retry with jitter makes it invisible under normal load, a benchmark tells you the price you're paying and where the ceiling is, and idempotency makes sure the retries — at every layer — never turn into double-spends.

**Code:** [github.com/xidoke/ledger-service](https://github.com/xidoke/ledger-service) — the [concurrency model](https://github.com/xidoke/ledger-service/blob/main/docs/architecture/concurrency-model.md) doc and ADRs [0005](https://github.com/xidoke/ledger-service/blob/main/docs/adr/0005-ledger-model.md), [0006](https://github.com/xidoke/ledger-service/blob/main/docs/adr/0006-balance-representation.md), [0011](https://github.com/xidoke/ledger-service/blob/main/docs/adr/0011-concurrency-strategy.md), [0012](https://github.com/xidoke/ledger-service/blob/main/docs/adr/0012-idempotency.md) go deeper. **Live demo:** [ledger-service-bjzr.onrender.com](https://ledger-service-bjzr.onrender.com) (free instance — first request cold-starts ~50 s).
