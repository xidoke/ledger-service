# Architecture Decision Records

Captures architectural decisions — **what** was decided, **when**, and **why** — as immutable records. ADRs are the canonical reference when the codebase makes a non-obvious choice.

**Diátaxis**: Explanation. **Status**: scaffold; LDG-16 fills ADR-001..007.

## Convention

- **Format**: [MADR](https://adr.github.io/madr/) (Markdown Architectural Decision Records) — 4 sections: Status / Context / Decision / Consequences (+ optional Alternatives, Pros & Cons).
- **Naming**: `NNNN-title-with-dashes.md` (4-digit zero-padded, kebab-case title). Numbers never reused.
- **Immutability**: an Accepted ADR is **not edited** — it is superseded by a new ADR that references the old one in its Status line (`Supersedes ADR-NNNN`).
- **Cross-link**: each ADR notes its source analysis path in the Obsidian vault (`Research/ledger-system-ADR/wiki/<adr-name>`).

## Planned content (Phase 0 LDG-16)

|               ADR                |                       Decision                       |
|----------------------------------|------------------------------------------------------|
| `0001-architectural-style.md`    | Monolith + double-entry, **not** full event-sourcing |
| `0002-language-framework.md`     | Java 21 + Spring Boot 3 + Maven                      |
| `0003-database.md`               | PostgreSQL + Flyway                                  |
| `0004-package-structure.md`      | Package-by-feature                                   |
| `0005-ledger-model.md`           | Double-entry append-only                             |
| `0006-balance-representation.md` | Balance cached in same transaction as ledger entries |
| `0007-money-representation.md`   | Integer minor units                                  |

Phase 1+: ADRs 008-017 as new design decisions land.

## References

- MADR template: <https://github.com/adr/madr>
- General ADR background: <https://adr.github.io/>
