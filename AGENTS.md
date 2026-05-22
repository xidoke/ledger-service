# AGENTS.md

> Tool-neutral AI-agent instructions for `ledger-service`.
> **Claude Code users**: also read `CLAUDE.md` (Claude-specific delta) and the
> per-package `CLAUDE.md` files under each feature package.

## Mission

`ledger-service` is a mini e-wallet backend built on a double-entry ledger:
immutable, append-only ledger entries with balance kept as a same-transaction
projection cache — a modular monolith on PostgreSQL, deliberately not full event
sourcing.

## Stack

|      Layer      |                            Technology                            |
|-----------------|------------------------------------------------------------------|
| Language        | Java 21 (LTS, Eclipse Temurin recommended)                       |
| Framework       | Spring Boot 3.5                                                  |
| Build           | Maven — always use `./mvnw` (wrapper), never system `mvn`        |
| Database        | PostgreSQL 17 via Flyway migrations                              |
| Tests           | JUnit 5 + AssertJ (unit) · Testcontainers/Postgres (integration) |
| Static analysis | Error Prone + NullAway · SpotBugs · ArchUnit                     |
| Format          | Spotless + Palantir Java Format (4-space, 120-col)               |
| Local infra     | Docker Compose — Spring Boot auto-starts Postgres                |

## Commands

```bash
./mvnw verify                                  # build + test + format-check + JaCoCo gate (the CI command)
./mvnw spotless:apply                          # auto-format Java + pom.xml + Markdown (run before committing)
./mvnw spring-boot:run                         # run locally; auto-starts Postgres via compose.yaml
./mvnw spring-boot:test-run                    # ephemeral mode (Testcontainers; DB resets on restart)
docker compose -f compose.app.yaml up --build  # full stack in containers (app + Postgres)
./mvnw spotless:check                          # verify formatting only (CI gate)
```

After clone, install Git hooks once: `brew install lefthook` then `lefthook install`
(wires `pre-commit` Spotless auto-format + `commit-msg` Conventional Commits check).

## Package layout

Application code: `src/main/java/com/xidoke/ledger/` — **package-by-feature** (ADR-0004).

|    Package     |                                 Responsibility                                  |
|----------------|---------------------------------------------------------------------------------|
| `account/`     | Account entity, balance cache, optimistic-locking `version` column              |
| `transfer/`    | Transfer use case — two-leg double-entry posting between accounts               |
| `topup/`       | Top-up use case — credits an account against the `SYSTEM_FUNDING` counterpart   |
| `ledger/`      | `LedgerEntry` (append-only), the double-entry posting primitive, reconciliation |
| `idempotency/` | Idempotency-key storage and the dedup guard for mutating endpoints              |
| `outbox/`      | Transactional outbox: event row written in the same posting transaction         |
| `common/`      | Cross-cutting only — Spring config, security skeleton, web error handling       |

Schema migrations: `src/main/resources/db/migration/V<n>__description.sql` (Flyway).

## Conventions

**Commit** — [Conventional Commits 1.0](https://www.conventionalcommits.org/):
`<type>(<scope>): <description>` + body (WHY, wrap 72) + footer `Refs LDG-N` (intermediate) or `Closes LDG-N` (closing).
- type: `feat | fix | docs | refactor | test | perf | build | ci | chore | style | revert`
- scope (optional): package (`account`/`transfer`/`topup`/`ledger`/`idempotency`/`outbox`/`common`) or area (`security`/`web`/`infra`/`deps`/`release`)
- description: imperative, lowercase first letter, no trailing period, ≤ 72 chars

**Branch** — `<type>/ldg-<num>-<short-slug>` (lowercase kebab-case; `ldg-<num>` required for Linear auto-link). Example: `feat/ldg-12-postgres-flyway`.

**File naming** — tests `*Test.java` (Surefire unit) / `*IT.java` (Failsafe + Testcontainers); Flyway `V<n>__snake_case.sql` (sequential, no gaps); ADRs `docs/adr/NNNN-kebab-title.md` (MADR).

**Code style** — Palantir format via Spotless; constructor injection only (no field `@Autowired`); `@NullMarked` project-wide (NullAway); money is `long` BIGINT minor units, never `float`/`double` (ADR-0007); inter-module access via the service layer, never another feature's repository (ADR-0004).

**Testing** — unit = JUnit 5 + AssertJ, no Testcontainers/network; integration = Testcontainers Postgres in `*IT.java` (no H2); JaCoCo ≥ 70% instruction gate enforced by `./mvnw verify`.

## Invariants — never violate

- `ledger_entries` is append-only: no `UPDATE`/`DELETE` on posted rows; corrections are new reversing entries.
- Every transaction balances: `Σ DEBIT == Σ CREDIT` per posting.
- `accounts.balance` is a cache, updated in the **same ACID transaction** as its entries; it can always be rebuilt from `ledger_entries`.
- `common/` must not import any feature package — dependencies point inward only.

## DO

- Run `./mvnw spotless:apply` before staging any Java, `pom.xml`, or Markdown.
- Run `./mvnw verify` after non-trivial changes (tests + coverage gate).
- Stage specific files by name (`git add src/...`), never `git add -A`/`.`.
- Add a new Flyway migration (`V<n+1>__...sql`) for every schema change.
- Read the owning feature package's `CLAUDE.md` before touching domain code (Phase 1+).
- Consult `docs/glossary.md` when a domain term is ambiguous.

## DON'T

- Don't edit/delete an already-applied Flyway migration — write a new one.
- Don't use `float`/`double` for money — use `long` minor units.
- Don't import another feature's repository from a sibling feature; don't import any feature from `common/`.
- Don't use Lombok in entities; don't commit secrets/`.env`; don't force-push `main`.
- Don't bypass hooks with `--no-verify` — fix the formatting/message instead.
- Don't push, open PRs, merge, or set Linear status to `Done`/`Canceled`/`Blocked` — those are the maintainer's gate.
- Don't edit `application.yml` or change `pom.xml` dependencies without ADR rationale.
- Don't edit an `Accepted` ADR — supersede it with a new one.

## Quality gates (CI enforces)

|     Gate      |                Tool                 |           Threshold            |
|---------------|-------------------------------------|--------------------------------|
| Format        | Spotless (`spotless:check`)         | zero drift                     |
| Null safety   | Error Prone + NullAway              | Phase 0: warn; Phase 1+: error |
| Bytecode bugs | SpotBugs (`effort=Max`)             | Phase 0: warn; Phase 1+: error |
| Architecture  | ArchUnit (`LedgerArchitectureTest`) | enforced                       |
| Coverage      | JaCoCo                              | ≥ 70% instruction              |

## Context hierarchy

- `AGENTS.md` (this file) — tool-neutral single source of truth.
- `CLAUDE.md` — Claude Code delta only (skills, hooks, vault KB pointers); does not duplicate this file.
- `src/main/java/com/xidoke/ledger/<feature>/CLAUDE.md` — per-package domain rules, lazy-loaded when you read that package (Phase 1 fills these).

## Further reading

- [ARCHITECTURE.md](ARCHITECTURE.md) — module map, invariants, data flow, ADR index
- [CONTRIBUTING.md](CONTRIBUTING.md) — full commit/branch/PR spec, code style, testing
- [docs/adr/](docs/adr/) — Architecture Decision Records (MADR; 0001–0007 in Phase 0)
- [docs/glossary.md](docs/glossary.md) — ubiquitous language; resolve term ambiguity here
- [README.md](README.md) — product overview, quickstart, roadmap
