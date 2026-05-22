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
| [0019](0019-ddd-tactical-patterns.md)  | DDD tactical patterns; LedgerEntry as a shared immutable fact                          | Accepted |
| [0031](0031-identifier-strategy.md)    | UUID app-generated ids (distributed-ready; v4 → v7)                                    | Accepted |

Phase 1 ADRs (0008-0017) land as decisions land — currency scope, system funding account, aggregate boundary, concurrency strategy, idempotency, event publishing (outbox), PII handling, event schema versioning, reconciliation, observability.

> **Numbering note**: ADR numbers track the vault decision log (`Research/ledger-system-ADR/wiki/adr-NNN-*`), not repo creation order, so each repo ADR keeps the same number as its source analysis. A decision is distilled here when its code lands — hence numbers may be non-contiguous (e.g. 0008 currency-scope and 0009 system-funding-account are still pending while 0010 is distilled). Numbers are never reused.

## Convention

- **Format**: [MADR](https://adr.github.io/madr/) (Markdown Architectural Decision Records). Each ADR has Status / Context / Decision drivers / Considered options (Pros & Cons each) / Decision outcome / Consequences / Risks & open questions / References.
- **Naming**: `NNNN-title-with-dashes.md` (4-digit zero-padded, kebab-case title). Numbers never reused — rejected ADRs keep their slot with status `Rejected`.
- **Immutability**: an Accepted ADR is **not edited** — it is superseded by a new ADR that references the old one in its Status line (`Supersedes ADR-NNNN`).
- **Cross-link**: each ADR notes its source analysis path in the Obsidian vault (`Research/ledger-system-ADR/wiki/adr-NNN-<title>.md`) for the original deep analysis.

## References

- MADR template: <https://github.com/adr/madr>
- General ADR background: <https://adr.github.io/>
- adr-numbering-and-status conventions (in vault): `Research/repo-docs-organization/wiki/adr-numbering-and-status.md`
