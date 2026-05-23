# Architecture Decision Records

Captures architectural decisions — **what** was decided, **when**, and **why** — as immutable records. ADRs are the canonical reference when the codebase makes a non-obvious choice.

**Diátaxis**: Explanation.

## Catalog

|                  ADR                   |                                         Title                                          |  Status  |
|----------------------------------------|----------------------------------------------------------------------------------------|----------|
| [0001](0001-architectural-style.md)    | Modular monolith + double-entry append-only on PostgreSQL, **not** full event sourcing | Accepted |
| [0002](0002-language-framework.md)     | Java 21 + Spring Boot 3                                                                | Accepted |
| [0003](0003-database.md)               | PostgreSQL                                                                             | Accepted |
| [0004](0004-package-structure.md)      | Package-by-feature                                                                     | Accepted |
| [0005](0005-ledger-model.md)           | Double-entry, append-only ledger entries                                               | Accepted |
| [0006](0006-balance-representation.md) | Balance cached in the same DB transaction as ledger entries                            | Accepted |
| [0007](0007-money-representation.md)   | BIGINT integer minor units                                                             | Accepted |
| [0008](0008-currency-scope.md)         | Single currency at Phase 0-1 (defer multi-currency + FX)                               | Accepted |
| [0009](0009-system-funding-account.md) | SYSTEM_FUNDING counterpart so top-ups stay balanced                                    | Accepted |
| [0010](0010-aggregate-boundary.md)     | Account-per-aggregate (Account is the locking boundary)                                | Accepted |
| [0011](0011-concurrency-strategy.md)   | Optimistic locking (`@Version`) + bounded retry; pessimistic benchmarked               | Accepted |
| [0012](0012-idempotency.md)            | `Idempotency-Key` header + `idempotency_keys` table (claim-first in-flight)            | Accepted |
| [0017](0017-observability.md)          | Structured JSON log + MDC correlation id + Spring Actuator                             | Accepted |
| [0018](0018-hexagonal-architecture.md) | Hexagonal (Ports & Adapters) inside each module                                        | Accepted |
| [0019](0019-ddd-tactical-patterns.md)  | DDD tactical patterns; LedgerEntry as a shared immutable fact                          | Accepted |
| [0031](0031-identifier-strategy.md)    | UUID app-generated ids (distributed-ready; v4 → v7)                                    | Accepted |

The remaining Phase-1 ADRs land as their code lands: event publishing/outbox (0013), PII handling (0014), event schema versioning (0015), reconciliation (0016) — still in the vault, each with a write-issue at its milestone. The deferred Phase-B decisions (0020–0030: resilience, rate-limiting, caching, gateway, saga, DLQ, inbox, deployment, ACL, strangler-fig) stay vault-only until there is code to record.

> **Numbering note**: ADR numbers track the vault decision log (`Research/ledger-system-ADR/wiki/adr-NNN-*`), not repo creation order, so each repo ADR keeps the same number as its source analysis. A decision is distilled here when its code lands — hence numbers may be non-contiguous (e.g. 0013–0016 are not yet distilled while 0011/0012/0017/0018/0019 are). Numbers are never reused.

## Convention

- **Format**: [MADR](https://adr.github.io/madr/) (Markdown Architectural Decision Records). Each ADR has Status / Context / Decision drivers / Considered options (Pros & Cons each) / Decision outcome / Consequences / Risks & open questions / References.
- **Naming**: `NNNN-title-with-dashes.md` (4-digit zero-padded, kebab-case title). Numbers never reused — rejected ADRs keep their slot with status `Rejected`.
- **Immutability**: an Accepted ADR is **not edited** — it is superseded by a new ADR that references the old one in its Status line (`Supersedes ADR-NNNN`).
- **Cross-link**: each ADR notes its source analysis path in the Obsidian vault (`Research/ledger-system-ADR/wiki/adr-NNN-<title>.md`) for the original deep analysis.

## References

- MADR template: <https://github.com/adr/madr>
- General ADR background: <https://adr.github.io/>
- adr-numbering-and-status conventions (in vault): `Research/repo-docs-organization/wiki/adr-numbering-and-status.md`
