# account package — CLAUDE.md

Owns the `Account` aggregate root and locking boundary (ADR-0010): cached `balance` + `version`, `status`, and the `debit`/`credit` domain methods that enforce the per-account invariants (account ACTIVE; no overdraw). Repository, service, and the optimistic-locking persistence wiring (ADR-0006, ADR-0011) land in later issues.

See repository root `CLAUDE.md` for project-wide conventions.
