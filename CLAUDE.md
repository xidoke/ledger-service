# CLAUDE.md

Project-specific guidance for Claude Code when working in this repo. Read alongside [README.md](README.md) (what + quickstart) and [CONTRIBUTING.md](CONTRIBUTING.md) (conventions). For project knowledge base (planning, ADRs, roadmap, research notes), see the Obsidian vault — paths below.

> **Migration note (2026-05-22)**: this file is currently canonical because Claude is the only AI agent used here. When LDG-36 lands `AGENTS.md` as the cross-tool single source, this file becomes a small Claude-only delta — see `Research/repo-docs-organization/wiki/ai-context-files-duplication.md` for the consolidation pattern.

## Project overview

`ledger-service` is a mini e-wallet backend on a **double-entry ledger**. Stack: Java 21 + Spring Boot 3.5 + PostgreSQL + Flyway. Phase 0 = skeleton + tooling stack; Phase 1 = core ledger (Nấc 0-1). Modular monolith, **not** event sourcing (per ADR-001). See [README.md](README.md#what-it-does) for product context.

## Build / test / run

```bash
./mvnw verify            # build + test + spotless:check (+ JaCoCo once LDG-22 lands)
./mvnw spring-boot:run   # local app on :8080 (auto-starts Postgres via compose.yaml)
./mvnw spotless:apply    # auto-format Java + pom.xml + Markdown (Palantir, 4-space)
docker compose up        # alternative: full stack via Compose
```

**Always use `./mvnw`** (Maven wrapper), not system `mvn` — wrapper pins the Maven version.

## Key paths

- `src/main/java/com/xidoke/ledger/` — application code, **package-by-feature** (`account/`, `transfer/`, `topup/`, `ledger/`, `idempotency/`, `outbox/`, `common/`). Each feature package has its own `CLAUDE.md` placeholder (Phase 1 fills domain rules; lazy-loaded when you read code in the package).
- `src/main/resources/application.yml` — Spring config; do NOT modify without ADR rationale.
- `src/main/resources/db/migration/V*.sql` — Flyway migrations; **immutable once applied** — write a new migration instead of editing.
- `docs/adr/` — Architecture Decision Records (MADR format). ADR-001..007 land in LDG-16.
- `docs/glossary.md` — domain term dictionary (ubiquitous language). Defer to it when a term is ambiguous.
- `pom.xml` — dependencies; new deps require ADR + Dependabot review.
- `lefthook.yml` — pre-commit (Spotless) + commit-msg (Conventional Commits regex). Run `brew install lefthook && lefthook install` once after clone.

## Conventions

- **Commit / branch**: [Conventional Commits 1.0](https://www.conventionalcommits.org/) + footer `Refs LDG-N` / `Closes LDG-N` for Linear auto-link. Branch pattern `<type>/ldg-N-<short-slug>`. Full spec: [CONTRIBUTING.md](CONTRIBUTING.md).
- **Code style**: Palantir Java Format (4-space, 120-col) enforced by Spotless. Pre-commit hook auto-formats; CI `mvn spotless:check` fails on drift.
- **Tests**: JUnit 5 + AssertJ for unit (`*Test.java`); Testcontainers Postgres for integration (`*IT.java`). Concurrency tests required for any code touching ledger writes (Phase 1+).
- **ADRs**: every non-obvious decision goes in `docs/adr/NNNN-title.md` (MADR), **immutable once Accepted** — supersession only.

## Cross-reference — vault knowledge base

The Obsidian vault at `/Users/xidoke/Library/Mobile Documents/iCloud~md~obsidian/Documents/Obsidian_Vault` holds the project knowledge base. Useful entry points:

- `Dự án - Ledger Service/CLAUDE.md` — vault-side agent context (planning + roadmap framing)
- `Dự án - Ledger Service/30 - Bản đồ tri thức.md` — master map: research × phase × ADR × LDG
- `Dự án - Ledger Service/50 - Phases/Phase 0 - Skeleton.md` — current phase plan with all LDG mappings
- `Research/linear-issue-management/wiki/ledger-service-applied.md` — Linear conventions (branch, commit, label, milestone)
- `Research/java-project-setup/wiki/<note>` — toolchain decisions per topic
- `Research/ledger-systems/wiki/<note>` — domain (double-entry) concepts (Phase 1+ reading)

When the user asks about a domain concept (idempotency, outbox, optimistic locking, etc.), **read the corresponding vault wiki first** before answering from general knowledge.

## Boundaries — do NOT without asking

- Modify `application.yml` or `pom.xml` dependencies (need ADR rationale)
- Edit a Flyway migration that is already applied (write a new one)
- Push branches, open PRs, merge, or set Linear status `Done` / `Canceled` / `Blocked` — those are the user's gate (per `Research/linear-issue-management/wiki/ai-agent-operating-rules.md`)
- Bypass hooks with `--no-verify` — fix the root cause (formatting, message regex)
- `git add -A` — always stage specific files by name

## Output preferences

- **Vietnamese** for prose explanations to the user; **English** for code, commit messages, identifiers, ADR text
- Brief responses; cite files with `path:line` instead of pasting large code blocks
- For diffs > 5 context lines, summarize rather than dump
- Use Markdown tables for structured info (files, decisions, AC checklist)

## Workflow shortcut

End-to-end LDG-N execution is automated by the `ledger-task` skill (user-level `~/.claude/skills/ledger-task/`). Triggers: "tiếp tục LDG-N", "làm LDG-N", `/ledger-task N`. The skill handles `Todo → In Progress → In Review` transitions; the user keeps the gate on push, PR, merge, and `Done`.

## References

- [README.md](README.md) — product overview + quickstart
- [CONTRIBUTING.md](CONTRIBUTING.md) — full convention spec (commits, branches, PR, code style, testing)
- [docs/](docs/) — ADRs, architecture, glossary, runbooks, onboarding
- [LICENSE](LICENSE) — MIT
