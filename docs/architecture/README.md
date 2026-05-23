# Architecture deep dives

Per-subsystem architecture documentation expanding on the root [`ARCHITECTURE.md`](../../ARCHITECTURE.md) country-map. Includes C4 diagrams (Context / Container / Component) and prose explanations of cross-cutting flows.

**Diátaxis**: Explanation. The root [`ARCHITECTURE.md`](../../ARCHITECTURE.md) carries the C4 *Container* view; these files are the *Component*/flow deep dives.

## Contents

- [`data-model.md`](data-model.md) — Account / Transaction / LedgerEntry entities + invariants
- [`posting-flow.md`](posting-flow.md) — transfer/top-up posting sequence (idempotency → @Transactional → outbox)
- [`outbox-flow.md`](outbox-flow.md) — event publishing path (write → poll → idempotent consume) + reconciliation
- [`concurrency-model.md`](concurrency-model.md) — optimistic locking + retry, with the benchmark numbers

## Convention

- **One file per subsystem or concern** — don't blend multiple bounded contexts in one file.
- **kebab-case** filenames, no numbered prefix (no inherent reading order — files are consulted on demand).
- **Cross-link** to related ADRs and code package paths.
- **Embed Mermaid diagrams** directly in Markdown when possible (renders on GitHub) — fall back to external `.mmd` or `.puml` for complex diagrams.

## References

- [C4 model](https://c4model.com/) — Context / Container / Component / Code
- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md) (Phase 0 LDG-31)
