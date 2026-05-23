# Changelog

All notable changes to ledger-service are documented here.

Format: [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Add entries under the relevant heading as PRs merge; on release, rename this
section to the new version + date and start a fresh `[Unreleased]`.

### Added

- Core domain model (Phase 1): `Account` aggregate (balance cache, status, `debit`/`credit` enforcing ACTIVE + no-overdraw), `Transaction` aggregate (double-entry `Σ debit == Σ credit` posting), immutable `LedgerEntry` fact, and a shared domain kernel (`Money`, `AccountId`, `TransactionId`, `Direction`) under `common/domain`. ADR-0010 (Account-per-aggregate).
- Flyway migrations V2–V4 for the domain schema: `accounts` (UUID PK, BIGINT `balance`, optimistic-lock `version`, `updated_at` trigger), `transactions`, and append-only `ledger_entries` (DB trigger rejecting UPDATE/DELETE, `amount > 0` check, `(account_id, created_at)` index). Money columns are BIGINT minor units (ADR-0007); enum-like columns use VARCHAR + CHECK.
- `MoneyFormatter` — currency-aware display formatting (minor → major via ISO 4217 fraction digits), centralizing the `/100` conversion so it never scatters (ADR-0007).
- Account persistence foundation (hexagonal, ADR-0018): `account/` split into `domain/` (pure `Account` aggregate + `AccountRepository` port) and `adapter/out/` (`AccountJpaEntity` + mapper + `AccountPersistenceAdapter` over Spring Data JPA, optimistic-lock `@Version`). ArchUnit now enforces that `..domain..` is free of JPA/Spring.
- Account endpoints: `POST /accounts`, `GET /accounts/{id}`, `GET /accounts/{id}/entries` (entry history via a `JdbcClient` read query). Unknown id → RFC 7807 404; validation failures → 400 with `fieldErrors`. A shared `common/error/NotFoundException` maps to 404 centrally so domain exceptions stay framework-free.
- Top-up use case: `POST /accounts/{id}/topups` posts a balanced `DEBIT SYSTEM_FUNDING / CREDIT user` pair in one `@Transactional` (balance cache + entries commit together, ADR-0006); transaction header + append-only entries via `JdbcClient`. Adds `AccountType` (USER/SYSTEM) + the `account_type` column (V5) so SYSTEM_FUNDING may hold a negative balance (ADR-0009), and seeds the SYSTEM_FUNDING account. ArchUnit now permits use-case features (topup/transfer) to depend on core features (account/ledger) per ADR-0019.
- Transfer use case: `POST /transfers` posts a balanced `DEBIT from / CREDIT to` pair between two user accounts in one `@Transactional`, with both balance caches committed alongside the entries (ADR-0006). Insufficient funds, self-transfer, and currency mismatch are rejected as RFC 7807 `422` via a new `common/error/UnprocessableEntityException` family (mirroring the 404 `NotFoundException` family; kept distinct from 409, reserved for optimistic-lock conflict in M4). Failures roll the whole posting back — proven by a Testcontainers integration test.
- Idempotency-Key middleware (ADR-0012, Stripe-style): an `IdempotencyFilter` (`OncePerRequestFilter`) on mutating `POST`s hashes `method + path + body` (SHA-256) and, against a new `idempotency_keys` table (Flyway V6), replays the stored response on a same-key retry, runs once on a miss (storing only 2xx outcomes), and rejects a key reused with a different body as RFC 7807 `422`. Backed by a Testcontainers integration test.
- Idempotency made mandatory + concurrency-safe on the money endpoints: `POST /transfers` and `POST /accounts/{id}/topups` now **require** `Idempotency-Key` (missing → `400`). The filter claims the key up front via `INSERT … ON CONFLICT DO NOTHING` (PENDING row, Flyway V7), so a concurrent same-key request that loses the claim gets `409` while the first is in flight; a failed operation releases the key so it stays retryable. Proven by integration tests (replay, `422` mismatch, `400` missing, `409` in-flight).
- ADR-0012 (idempotency) distilled into `docs/adr/`, reconciled with what shipped (claim-first separate-commit, explicit `status` column, 400/409/422 taxonomy). A two-thread Testcontainers concurrency test proves the exactly-once guarantee under a real same-key race.
- Optimistic-lock retry on the money endpoints (ADR-0011): a bounded `OptimisticRetry` helper retries `/transfers` and `/accounts/{id}/topups` on an `OptimisticLockingFailureException` (capped attempts + exponential backoff with full jitter, configurable under `ledger.retry.*`); each attempt is a fresh transaction that reloads the account at its current `@Version`. Retry exhaustion → RFC 7807 `409` (new `ConcurrencyConflictException`), not a `500`. Proven by a moderate-contention stress test where every transfer succeeds once conflicts are retried; the `@Version` column (already present) is what keeps the cache consistent under the race.
- Property-based invariant test (jqwik): generates random sequences of top-ups/transfers over the real domain aggregates and asserts the books always balance — `Σ DEBIT == Σ CREDIT`, per-account `balance == Σ signed entries`, and system-wide `Σ all balances == 0` (ADR-0005). Pure-domain (no Spring/DB), 500 tries with shrinking + deterministic seed on failure. jqwik pinned to 1.9.2 to match the Spring-Boot-managed JUnit Platform 1.12.x.
- ADR-0011 (concurrency strategy) distilled into `docs/adr/` with **measured numbers**: a `@Tag("benchmark")` `TransferConcurrencyBenchmark` (excluded from the normal/CI build, run via `./mvnw test -Pbenchmark`) compares optimistic+retry vs pessimistic `SELECT … FOR UPDATE` on the identical transfer body. Low-contention is a tie (optimistic wastes no retries and never blocks reads); on a single hot row pessimistic is ~2× faster while optimistic burns retries — confirming optimistic+retry for the read-heavy profile and quantifying the threshold past which a hot account needs async/sharding. A Maven `benchmark` profile keeps the timing tests out of CI.
- Idempotency key reaper (ADR-0012): a `@Scheduled` `IdempotencyKeyReaper` sweeps `idempotency_keys` so abandoned keys stop blocking retries — deletes `PENDING` rows past a short max-in-flight window (a claim orphaned by a crash between claim and complete, which would otherwise 409 forever) and `COMPLETED` rows past the client retry window. Windows + cadence configurable under `ledger.idempotency.reaper.*` (defaults: 10m / 24h / 10m); `@EnableScheduling` enabled on the app. Backed by a Testcontainers test (stale rows reaped, in-window rows kept, a swept key is reusable).
- OpenAPI 3 spec via SpringDoc (`springdoc-openapi-starter-webmvc-ui` 2.8.17): live `/v3/api-docs`(`.yaml`) + Swagger UI at `/swagger-ui.html`, and a committed [`docs/api/openapi.yaml`](docs/api/openapi.yaml) covering accounts, top-ups, and transfers. An `OpenApiConfig` customizer documents what the controller signatures cannot — the required `Idempotency-Key` header on the money endpoints and the RFC 7807 `application/problem+json` `400`/`409`/`422` shapes. The spec is regenerated by `OpenApiDocsTest` (`-Dopenapi.dump`), which also asserts its contents in CI.
- Transactional outbox — write side (ADR-0013): a new `outbox` table (Flyway V8; `id BIGINT IDENTITY`, `aggregate_id`, `event_type`, `payload JSONB`, `status` PENDING/SENT, `schema_version`, partial index on PENDING) plus an `OutboxRepository` port + JdbcClient store. `TransferService`/`TopupService` append a `TransferPosted`/`TopupPosted` event in the **same transaction** as the ledger write, so the event can never be lost or orphaned (no dual-write) — proven by a Testcontainers test where a rolled-back posting leaves neither an outbox row nor a ledger entry. The poller (read side) lands in LDG-55. ADR-0013 distilled to `docs/adr/`.
- Transactional outbox — read side (ADR-0013): a `@Scheduled` `OutboxPoller` drains PENDING events oldest-first (`FOR UPDATE SKIP LOCKED`, so concurrent pollers take disjoint batches), "publishes" each (log-only at Nấc 0 — no broker yet), and marks them SENT with `published_at`; a publish that throws leaves the row PENDING for the next tick (at-least-once). The **idempotent-consumer skeleton** (`LoggingIdempotentConsumer`) dedups by event id via a new `processed_events` inbox (Flyway V9, `INSERT … ON CONFLICT DO NOTHING`), so a redelivered event runs its side effect at most once — proven by a Testcontainers test feeding a duplicate. Cadence configurable under `ledger.outbox.*`.
- Reconciliation drift job (ADR-0016): a `@Scheduled` `ReconciliationJob` (default 02:00 daily, read-only) re-derives each account's balance from the immutable ledger and flags any account whose cached `accounts.balance` ≠ `Σ` signed entries, plus a system-wide trial-balance check (`Σ debit − Σ credit` must be 0). Drift is logged at ERROR with both values and published as a Micrometer gauge (`ledger.reconciliation.balance_drift_accounts`) — **never auto-corrected** (an operator investigates and posts a correcting entry). Proven by a Testcontainers test that injects a deliberate drift. Cron under `ledger.reconciliation.cron`; ADR-0016 distilled to `docs/adr/`.
- ADR-0014 (PII handling) distilled to `docs/adr/`: the **forgettable-payload** decision — PII never enters the append-only `ledger_entries`/`transactions`/outbox (they carry only opaque `account_id` UUIDs), so a GDPR/NĐ-13 erasure hard-deletes the mutable identity row while the immutable financial facts survive. Confirmed the codebase already complies (`owner_ref` lives only on the mutable `accounts` table); a dedicated `customers` table is deferred to Phase 3+. With this, all Phase-1 ADRs (0001–0019, 0031) are distilled.
- ADR-0015 (event schema versioning) distilled to `docs/adr/`: the strategy for evolving outbox event schemas — an explicit `schema_version` (already shipped on the `outbox` table, V8) + tolerant reader (Spring Boot's default `FAIL_ON_UNKNOWN_PROPERTIES=false`), with a convention table (add optional field → bump version; semantic/structural change → new `event_type`) and linked-list upcasting for future structural changes.

### Changed

### Deprecated

### Removed

### Fixed

### Security

- Validate the inbound `X-Correlation-Id` against an allowlist (`[A-Za-z0-9_-]{1,64}`) before reflecting it into the response header / MDC — closes an HTTP response-splitting / log-injection vector in `CorrelationIdFilter`.

## [0.0.0] - 2026-05-22

Phase 0 — foundations & skeleton (no domain endpoints yet).

### Added

- Spring Boot 3.5 + Java 21 skeleton, package-by-feature layout, `/hello` smoke endpoint.
- PostgreSQL via Flyway with an env-driven datasource; Docker Compose for local Postgres plus a full-stack `compose.app.yaml` built from a multi-stage `Dockerfile`.
- Code-quality gate in `verify`: Spotless (Palantir), Error Prone + NullAway, ArchUnit (package-by-feature), SpotBugs, JaCoCo (70% instruction threshold).
- GitHub Actions CI (build + test + lint) with branch protection; coverage uploaded to Codecov.
- Observability: ECS structured JSON logging, `X-Correlation-Id` propagation, Actuator `health` / `info` / `metrics`.
- RFC 7807 ProblemDetail error handling.
- Docs: README, ARCHITECTURE.md, ADRs 0001–0007, glossary, CONTRIBUTING, MIT LICENSE; Lefthook pre-commit + commit-msg hooks.

[Unreleased]: https://github.com/xidoke/ledger-service/compare/v0.0.0...HEAD
[0.0.0]: https://github.com/xidoke/ledger-service/releases/tag/v0.0.0
