# transfer package — CLAUDE.md

The transfer use case (`POST /transfers`): move money between two **user** accounts as one balanced posting — `DEBIT from / CREDIT to` with `Σ DEBIT == Σ CREDIT` (ADR-0005). Unlike top-up (which is funded by `SYSTEM_FUNDING`), both legs are user accounts, so the source's no-overdraw invariant actually bites.

Structured hexagonally (ADR-0018), mirroring `account/` and `topup/`:

- `domain/` — transfer-specific business-rule exceptions (`SelfTransferException`, `SameCurrencyRequiredException`). No aggregate of its own: a transfer orchestrates the `Account` and `Transaction` aggregates.
- `application/TransferService` — `@Transactional` orchestration: self-check → load both accounts (404 if missing) → currency-check → `from.debit()` / `to.credit()` → `Transaction(TRANSFER).post()` → save both accounts + the transaction. Any rejection rolls the whole posting back (ADR-0006: the balance caches commit in the same transaction as the entries).
- `adapter/in/` — `TransferController` + request/response DTOs.

## Error mapping

Business-rule rejections extend `common/error/UnprocessableEntityException` → **HTTP 422** (insufficient funds, self-transfer, currency mismatch). This is kept distinct from 409 (reserved for optimistic-lock conflict in M4) and 400 (malformed request). Unknown account → 404 (`AccountNotFoundException`). See `common/web/ProblemDetailExceptionHandler` for the full status taxonomy.

## Not here yet

Idempotency (`Idempotency-Key`, M3 — ADR-0012) and concurrency retry (optimistic-lock, M4 — ADR-0011) wrap this path later.

See repository root `CLAUDE.md` for project-wide conventions.
