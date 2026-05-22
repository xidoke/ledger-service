# Changelog

All notable changes to ledger-service are documented here.

Format: [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Add entries under the relevant heading as PRs merge; on release, rename this
section to the new version + date and start a fresh `[Unreleased]`.

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

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
