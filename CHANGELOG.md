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
