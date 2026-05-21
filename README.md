# ledger-service

> Mini e-wallet / double-entry ledger service in Java 21 + Spring Boot 3.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Status:** Phase 0 — setup & skeleton. API not yet implemented.

## What it does

Backend service for an internal-style digital wallet: accounts, balances, top-ups, transfers, transaction history. Built on a **double-entry ledger**: ledger entries are immutable and append-only; balance is a projection derived from entries, kept as a same-transaction cache.

Design choice: **modular monolith + double-entry on relational DB**, *not* full event sourcing. This captures the audit-trail and immutability benefits of an event log for the ledger data without paying the full event-sourcing tax on the rest of the domain.

## Stack

| | |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.5 |
| Build | Maven (use the wrapper `./mvnw`, not a local Maven) |
| Database | PostgreSQL 17, schema via Flyway |
| Tests | JUnit 5, AssertJ, Testcontainers (Postgres) |
| Local infra | Docker Compose (Spring Boot starts it automatically) |

Package layout follows **package-by-feature**: `account/`, `transfer/`, `topup/`, `ledger/`, `idempotency/`, `outbox/`, `common/`. Feature packages are scaffolded empty in Phase 0; domain code lands in Phase 1.

## Prerequisites

- **JDK 21** — Eclipse Temurin recommended.
- **Docker** running (Docker Desktop, Colima, or OrbStack). Postgres runs as a container; you do **not** need a local Postgres install.
- **Git**.

## Quickstart

```bash
git clone <repo-url> ledger-service
cd ledger-service

# Verify Docker is up
docker info > /dev/null

# Run the app. Spring Boot auto-starts the Postgres container declared in compose.yaml.
./mvnw spring-boot:run

# In another shell — proof of life:
curl http://localhost:8080/hello
# → {"message":"ledger-service up"}
```

First run downloads Maven dependencies and pulls `postgres:17` (~5 min). Subsequent runs are ~10 s.

See [Basic usage](#basic-usage) below for the rest of the Phase 0 endpoints.

**Alternative — ephemeral mode** (uses Testcontainers; database resets every restart):

```bash
./mvnw spring-boot:test-run
```

## Basic usage

Phase 0 exposes only the skeleton endpoints — domain endpoints (`/accounts`, `/transfers`, `/topups`) land in Phase 1.

```http
GET /hello             → 200 {"message":"ledger-service up"}
GET /actuator/health   → 200 {"status":"UP"}
```

## Repository layout

```
ledger-service/
├── compose.yaml                       # Postgres container for local dev
├── pom.xml                            # Maven build + dependencies
├── mvnw, mvnw.cmd                     # Maven wrapper (always use these)
├── .mvn/wrapper/                      # Wrapper config
├── src/main/java/com/xidoke/ledger/   # Application code (package-by-feature)
│   ├── LedgerServiceApplication.java  # Spring Boot main class
│   ├── account/        # (empty in Phase 0)
│   ├── transfer/       # (empty in Phase 0)
│   ├── topup/          # (empty in Phase 0)
│   ├── ledger/         # (empty in Phase 0)
│   ├── idempotency/    # (empty in Phase 0)
│   ├── outbox/         # (empty in Phase 0)
│   └── common/
│       ├── security/   # SecurityConfig — permit-all skeleton, real auth deferred to Phase 3+
│       └── web/        # HelloController — health-style smoke endpoint
├── src/main/resources/
│   ├── application.yml                # Config
│   └── db/migration/                  # Flyway SQL migrations (`V<n>__description.sql`)
└── src/test/java/com/xidoke/ledger/
    ├── LedgerServiceApplicationTests.java
    ├── TestcontainersConfiguration.java
    └── TestLedgerServiceApplication.java
```

Doc artifacts (`ARCHITECTURE.md`, `docs/adr/`, `docs/onboarding/`) land during Phase 0 weeks 2–4 — see roadmap.

## Roadmap

The project advances through deliberate phases, each with explicit exit criteria:

- **Phase 0 (current, ~4 weeks)** — Foundations & skeleton: project files, code-quality stack (Spotless, ArchUnit, JaCoCo, SonarCloud), CI on GitHub Actions, Docker Compose + Flyway, structured logging + correlation ID, Actuator health groups.
- **Phase 1 (~8 weeks)** — Core ledger (level 0–1): double-entry entries, idempotency keys, optimistic locking with retry, transactional outbox, reconciliation job. Release **v0.1** deployed.
- **Phase 2+** — Two-service split via outbox boundary, saga for cross-service flows, Kafka, observability stack, scale work.

Detailed phase plans and architectural decision records are tracked in a personal knowledge base and will migrate into this repo under `docs/` during Phase 0.

## Further reading

The following docs land during Phase 0 — links are forward references:

- `ARCHITECTURE.md` — where to make a change (module map, key invariants). See [architecture-md-pattern](https://matklad.github.io/2021/02/06/ARCHITECTURE.md.html).
- `docs/adr/` — Architecture Decision Records (ADR-001..007 in Phase 0, ADR-008+ in Phase 1).
- `docs/onboarding/` — first-day setup beyond the Quickstart (IDE config, debug tips, common pitfalls).

## Contributing

Solo learning project for now — external PRs not accepted at this stage. A `CONTRIBUTING.md` will be added when the project reaches a stable shape.

Commit convention: [Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/).

## License

MIT — see [LICENSE](LICENSE). Copyright (c) 2026 xidoke.
