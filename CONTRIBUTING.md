# Contributing to ledger-service

Thanks for your interest in contributing.

This file is the contract for changing this repo: how to set up, what conventions to follow, how PRs are reviewed.

> Status: Phase 1 (core ledger) — single-author. Conventions designed to be team-portable.

## Quickstart

```bash
git clone <repo-url> ledger-service
cd ledger-service
./mvnw verify              # build + test
./mvnw spring-boot:run     # local app on :8080
```

Full prerequisites + Docker setup → see [README](README.md#prerequisites).

After clone, install Git hooks:

```bash
lefthook install
```

This activates pre-commit (Spotless auto-format) and commit-msg (Conventional Commits regex) hooks. Commits get rejected on format violations — fix locally before pushing.

## Branch naming

Pattern: `<type>/ldg-<num>-<short-slug>` — all kebab-case, lowercase.

- `<type>` one of: `feat | fix | chore | docs | refactor | test | perf | build | ci | hotfix | experiment | release`
- `ldg-<num>` **required** — Linear auto-links the branch to the issue (e.g., `LDG-27`)
- `<short-slug>` ≤ 5 words; capture essence

Examples:

- `feat/ldg-12-postgres-flyway` ✅
- `chore/ldg-18-project-files-baseline` ✅
- `docs/ldg-27-contributing-md` ✅
- `ldg-12-postgres-flyway` ❌ (missing type prefix)
- `feat/postgres-flyway` ❌ (missing `ldg-N` → no Linear auto-link)

Branch from `main`. Delete after merge.

## Commit format

[Conventional Commits 1.0](https://conventionalcommits.org). Pattern:

```
<type>(<scope>): <description>

[body — explain WHY, wrap 72]

Refs LDG-N
```

**Type** (commit-msg hook enforces):

|    Type    |                       When to use                       |      SemVer      |
|------------|---------------------------------------------------------|------------------|
| `feat`     | User-visible new behavior (endpoint, domain capability) | MINOR            |
| `fix`      | Production bug fix                                      | PATCH            |
| `docs`     | Docs only (README, ADR, comments)                       | —                |
| `refactor` | Code restructure, no behavior change                    | —                |
| `test`     | Add/fix tests                                           | —                |
| `perf`     | Performance improvement                                 | PATCH (optional) |
| `build`    | Build system, deps, tooling                             | —                |
| `ci`       | CI/CD configuration                                     | —                |
| `chore`    | Bootstrap, scaffolding, deps update                     | —                |
| `style`    | Whitespace, format                                      | —                |
| `revert`   | Revert previous commit                                  | depends          |

**Scope** (optional, matches package or area):

- Feature packages: `account`, `transfer`, `topup`, `ledger`, `idempotency`, `outbox`, `common`
- Cross-cutting: `security`, `web`, `infra`, `observability`, `deps`, `release`

**Description**: imperative present ("add", not "added"/"adds"), lowercase first letter, no trailing period, ≤ 72 chars.

**Body**: wrap at 72; explain **why** (the diff shows what); optional for trivial, required for non-trivial.

**Footer** (Linear references):

- `Refs LDG-N` — reference issue without closing (intermediate commit)
- `Closes LDG-N` — close issue when PR merges (lead commit or PR description)

Examples:

```
chore(common): scaffold package-by-feature layout

Adds empty feature packages (account, transfer, topup, ledger,
idempotency, outbox) with package-info.java placeholders so Git
tracks them. Adds HelloController in common/web returning 200.

Refs LDG-11
```

```
docs(readme): align with readme-md-anatomy spec

Adds Basic usage section + Further reading pointer per
the README anatomy convention.

Refs LDG-25
```

Historical note: Phase 0 skeleton work was mostly `chore` / `build` / `ci`; from Phase 1 on, user-visible domain endpoints land as `feat` (and their tests as `test`).

## Pull requests

1. Branch from `main` per [branch naming](#branch-naming).
2. Open PR — fill in the [PR template](.github/pull_request_template.md).
3. PR title = lead-commit conventional subject (e.g., `chore(common): scaffold package-by-feature layout`). Do **not** prefix `[LDG-N]` — Linear pulls from branch name.
4. PR description includes `Closes LDG-N` on its own line — Linear + GitHub auto-close on merge.
5. CI must pass (build + test + format check + static analysis).
6. Self-review checklist (solo dev, Phase 0): walk the diff thoroughly before merging.
7. Merge strategy: **squash merge**. Squash subject = lead commit (conventional format); `Closes LDG-N` stays in description.

## Code style

- **Format**: Spotless + Google Java Format. Run `./mvnw spotless:apply` to auto-format, `./mvnw spotless:check` to verify. CI fails on unformatted code.
- **Static analysis**: SpotBugs + Error Prone + NullAway run on `./mvnw verify`. Phase 0: warnings only (baseline); Phase 1+: treat as errors.
- **Architecture**: ArchUnit enforces package-by-feature (no cross-feature imports outside `common/`). Tests live in `src/test/java/.../LedgerArchitectureTest.java`.
- **Editor config**: `.editorconfig` at repo root — most IDEs auto-respect. IntelliJ: enable "Use editorconfig file" in Settings → Editor → Code Style.

## Testing

- **Unit tests**: JUnit 5 + AssertJ. File pattern `*Test.java` — runs in Surefire.
- **Integration tests**: `*IT.java` — runs in Failsafe with Testcontainers (real Postgres).
- **Coverage gate**: JaCoCo enforces ≥ 70% instruction coverage on `./mvnw verify`. Phase 1 raises to 85% for domain core.
- **Concurrency tests**: required for any change touching ledger writes — **write the failing test first** (Nấc 1 pattern). Spin N threads, assert invariants (no negative balance, no lost update).
- **Failure paths**: tests must cover error responses, not just happy path.

## Docs updates

When you change behavior, update accordingly:

- **README.md** — if Quickstart or Stack changes.
- **CHANGELOG.md** — every user-visible change goes under `## [Unreleased]` per [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
- **`docs/adr/`** — new architectural decisions land here. Numbered sequentially, MADR format.
- **ARCHITECTURE.md** — if module boundaries change.
- **`docs/glossary.md`** — new domain terms.

PRs that change behavior without docs update will be blocked.

## Reference

- [Conventional Commits 1.0](https://conventionalcommits.org)
- [Keep a Changelog 1.1](https://keepachangelog.com/en/1.1.0/)
- [Semantic Versioning 2.0](https://semver.org)
- [Spotless Maven Plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven)
- [Lefthook](https://lefthook.dev)
- ADR-002 (Java/Spring/Maven decision)
- ADR-004 (Package-by-feature decision)
