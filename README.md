# ledger-service

> Mini e-wallet / double-entry ledger service in Java 21 + Spring Boot 3.

[![CI](https://github.com/xidoke/ledger-service/actions/workflows/ci.yml/badge.svg)](https://github.com/xidoke/ledger-service/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/xidoke/ledger-service/graph/badge.svg)](https://codecov.io/gh/xidoke/ledger-service)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Status:** Phase 0 — setup & skeleton. API not yet implemented.

## What it does

Backend service for an internal-style digital wallet: accounts, balances, top-ups, transfers, transaction history. Built on a **double-entry ledger**: ledger entries are immutable and append-only; balance is a projection derived from entries, kept as a same-transaction cache.

Design choice: **modular monolith + double-entry on relational DB**, *not* full event sourcing. This captures the audit-trail and immutability benefits of an event log for the ledger data without paying the full event-sourcing tax on the rest of the domain.

## Stack

|             |                                                      |
|-------------|------------------------------------------------------|
| Language    | Java 21 (LTS)                                        |
| Framework   | Spring Boot 3.5                                      |
| Build       | Maven (use the wrapper `./mvnw`, not a local Maven)  |
| Database    | PostgreSQL 17, schema via Flyway                     |
| Tests       | JUnit 5, AssertJ, Testcontainers (Postgres)          |
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

**Alternative — full stack in containers** (app + Postgres, one command):

```bash
docker compose -f compose.app.yaml up --build
# Once both containers are healthy:
curl http://localhost:8080/hello
```

This builds the app image from the `Dockerfile` and runs it next to Postgres
(see `compose.app.yaml`). The `-f` is required: `compose.app.yaml` is kept out of
Docker/Spring auto-discovery so the `spring-boot:run` loop above (which uses the
postgres-only `compose.yaml`) is never disturbed. That loop is faster for daily
work; this mode mirrors a deployed environment. Stop with
`docker compose -f compose.app.yaml down`.

## Basic usage

Phase 0 exposes only the skeleton endpoints — domain endpoints (`/accounts`, `/transfers`, `/topups`) land in Phase 1.

```http
GET /hello             → 200 {"message":"ledger-service up"}
GET /actuator/health   → 200 {"status":"UP"}
GET /actuator/info     → 200 (build version + Java info)
GET /actuator/metrics  → 200 (Micrometer metric names)
```

Every request carries a correlation id: send `X-Correlation-Id` (or one is generated),
it's echoed on the response and included in every log line. Console logs are **ECS JSON**
(one object per line) — MDC fields like `correlationId` appear as top-level keys.

Errors are returned as **RFC 7807** `application/problem+json` (`type`, `title`, `status`,
`detail`, `instance`); validation failures add a `fieldErrors` object.

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

Architecture, decision records, and onboarding docs live in `ARCHITECTURE.md` and `docs/` — see [Further reading](#further-reading).

## Roadmap

The project advances through deliberate phases, each with explicit exit criteria:

- **Phase 0 (current, ~4 weeks)** — Foundations & skeleton: project files, code-quality stack (Spotless, ArchUnit, JaCoCo, SonarCloud), CI on GitHub Actions, Docker Compose + Flyway, structured logging + correlation ID, Actuator health groups.
- **Phase 1 (~8 weeks)** — Core ledger (level 0–1): double-entry entries, idempotency keys, optimistic locking with retry, transactional outbox, reconciliation job. Release **v0.1** deployed.
- **Phase 2+** — Two-service split via outbox boundary, saga for cross-service flows, Kafka, observability stack, scale work.

Architecture decision records live in [docs/adr/](docs/adr/) (0001–0007); the country-map overview is in [ARCHITECTURE.md](ARCHITECTURE.md). Detailed phase plans are tracked separately and surface under `docs/` as they solidify.

## Further reading

A layered skim path — start at the top, go deeper as the need arises:

- [ARCHITECTURE.md](ARCHITECTURE.md) — where to make a change: module map, invariants, data flow.
- [docs/adr/](docs/adr/) — Architecture Decision Records (0001–0007; 0008+ land in Phase 1).
- [docs/glossary.md](docs/glossary.md) — domain vocabulary: the ubiquitous language and canonical term names.
- [docs/onboarding/](docs/onboarding/) — first-day setup beyond the Quickstart (scaffold; fills in Phase 1).

## Code style

Spotless enforces formatting: **Palantir Java Format** (4-space, 120-col), sorted `pom.xml`, and Markdown via Flexmark.

```bash
./mvnw spotless:apply    # auto-format Java + pom + Markdown
./mvnw spotless:check    # verify only — exit !=0 if drift (CI gate)
```

`./mvnw validate` runs `spotless:check` automatically; CI will fail on unformatted code.

### Coverage

JaCoCo runs during `verify` and **fails the build below 70% instruction coverage**.

```bash
./mvnw verify                        # runs tests + the coverage gate
open target/site/jacoco/index.html   # browse the HTML report (use `xdg-open` on Linux)
```

CI uploads the same report as the `jacoco-report` artifact on every run.

### Static analysis

`verify` also runs three analyzers. In Phase 0 they are **warnings only** — they do
not fail the build yet (baseline first; treat-as-error comes in Phase 1).

- **Error Prone** + **NullAway** — compile-time bug patterns + null-safety (`@NullMarked` project-wide).
- **SpotBugs** — bytecode bug patterns (`effort=Max`).

Suppress a confirmed false positive as narrowly as possible:

```java
@SuppressWarnings("NullAway")  // Error Prone / NullAway (also "EqualsHashCode", etc.)

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "why it's safe here")  // SpotBugs
```

### Pre-commit hooks

Lefthook runs Spotless auto-format and validates commit messages against [Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/). Install once after clone:

```bash
brew install lefthook    # macOS — or download from https://lefthook.dev
lefthook install         # wire up .git/hooks → lefthook.yml
```

- `pre-commit`: `./mvnw spotless:apply` → re-stage → `./mvnw spotless:check`. Rejects commit if any drift remains.
- `commit-msg`: subject line must match `<type>(<scope>): <description>` (11 types, kebab-case scope). See `lefthook.yml` for the exact regex and [CONTRIBUTING.md](CONTRIBUTING.md#commit-format) for spec.

To bypass in emergencies: `git commit --no-verify` (skip both hooks). Use sparingly; CI still enforces format.

## Contributing

Solo learning project for now — external PRs not accepted at this stage. Conventions (branch naming, commit format, PR process, code style, testing expectations) are documented in [CONTRIBUTING.md](CONTRIBUTING.md) and designed to be team-portable.

Commit convention: [Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/) with `Refs LDG-N` / `Closes LDG-N` footer for Linear integration.

## License

MIT — see [LICENSE](LICENSE). Copyright (c) 2026 xidoke.
