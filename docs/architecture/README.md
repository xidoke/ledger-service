# Architecture deep dives

Per-subsystem architecture documentation expanding on the root [`ARCHITECTURE.md`](../../ARCHITECTURE.md) country-map. Includes C4 diagrams (Context / Container / Component) and prose explanations of cross-cutting flows.

**Diátaxis**: Explanation. **Status**: scaffold; Phase 1 fills as domain code lands. Phase 0 leaves root `ARCHITECTURE.md` as a high-level stub (LDG-31).

## Planned content (Phase 1)

- `data-model.md` — Account / Transaction / LedgerEntry entities + invariants
- `posting-flow.md` — transfer/topup sequence diagrams
- `outbox-flow.md` — event publishing path + reconciliation
- `concurrency-model.md` — optimistic locking + retry decision rationale
- C4 diagrams — Mermaid or Structurizr source files

## Convention

- **One file per subsystem or concern** — don't blend multiple bounded contexts in one file.
- **kebab-case** filenames, no numbered prefix (no inherent reading order — files are consulted on demand).
- **Cross-link** to related ADRs and code package paths.
- **Embed Mermaid diagrams** directly in Markdown when possible (renders on GitHub) — fall back to external `.mmd` or `.puml` for complex diagrams.

## References

- [C4 model](https://c4model.com/) — Context / Container / Component / Code
- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md) (Phase 0 LDG-31)
