# Posting flow

How a money mutation (top-up, transfer) becomes a balanced double-entry posting in one ACID transaction. Expands the [country map](../../ARCHITECTURE.md) §Data flow.

**Diátaxis**: Explanation. See ADR-0006 (one-tx balance + entries), ADR-0009 (SYSTEM_FUNDING), ADR-0011 (retry), ADR-0012 (idempotency), ADR-0013 (outbox).

## Transfer

```mermaid
sequenceDiagram
    actor C as Client
    participant F as IdempotencyFilter
    participant Ctl as TransferController
    participant R as OptimisticRetry
    participant S as TransferService (@Transactional)
    participant DB as PostgreSQL

    C->>F: POST /transfers + Idempotency-Key
    F->>DB: claim key (INSERT … ON CONFLICT) — PENDING
    Note over F: missing key → 400 · in-flight dup → 409 · replay → cached 2xx
    F->>Ctl: forward (first time)
    Ctl->>R: execute(transfer)
    R->>S: transfer(from, to, amount)   %% fresh tx each attempt
    S->>S: from.debit() / to.credit() / tx.post() (Σ DEBIT==Σ CREDIT)
    S->>DB: UPDATE accounts (balance + version) ·  INSERT transaction + 2 ledger_entries · INSERT outbox row
    Note over S,DB: one ACID commit — all or nothing (ADR-0006)
    alt @Version conflict
        DB-->>R: OptimisticLockingFailureException
        R->>S: retry (backoff+jitter, capped) — ADR-0011
    end
    S-->>F: 201
    F->>DB: store response COMPLETED
    F-->>C: 201 Created
```

## Notes

- **Top-up** is the same shape with one leg pre-set: `DEBIT SYSTEM_FUNDING / CREDIT user` (ADR-0009). `SYSTEM_FUNDING` is the contended row, so its top-ups are the hot path for retry (ADR-0011, [concurrency-model](concurrency-model.md)).
- **Idempotency runs in a servlet filter before the controller** (claim-first, committed separately so a concurrent same-key request sees the PENDING row under `READ COMMITTED`) — ADR-0012. A failed posting releases the key so the client can retry.
- **The outbox row is part of the same transaction** — no dual-write; a rollback leaves neither a ledger entry nor an event ([outbox-flow](outbox-flow.md), ADR-0013).
- **Rejections** roll the whole posting back: insufficient funds / self-transfer / currency mismatch → `422`; not-found → `404`; retry exhaustion → `409` (ADR-0011).
