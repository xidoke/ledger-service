# common package — CLAUDE.md

Cross-cutting infrastructure: configuration, error handling (`ProblemDetailExceptionHandler` — LDG-24), structured logging + correlation-id MDC (LDG-15), security baseline, web filters. Anything depended on by every feature package belongs here.

`common/domain/` is the **shared domain kernel**: value objects and immutable facts used across features — `Money`, `AccountId`, `TransactionId`, `Direction`, `LedgerEntry`. It lives in `common` because every feature may depend on it without creating a cross-feature dependency (ArchUnit only exempts `common` as the shared sink). `LedgerEntry` is modelled as a shared immutable fact (ADR-0005 log-is-truth) rather than a `Transaction` child — see vault DDD note `cross-aggregate-objects-and-the-ledger-entry-question`.

ArchUnit (LDG-17) enforces: `common/` is depended on by feature packages but never depends upward into them. See repository root `CLAUDE.md` for project-wide conventions.
