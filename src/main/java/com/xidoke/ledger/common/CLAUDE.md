# common package — CLAUDE.md

Cross-cutting infrastructure: configuration, error handling (`ProblemDetailExceptionHandler` — LDG-24), structured logging + correlation-id MDC (LDG-15), security baseline, money type, web filters. Anything depended on by every feature package belongs here.

ArchUnit (LDG-17) enforces: `common/` is depended on by feature packages but never depends upward into them. See repository root `CLAUDE.md` for project-wide conventions.
