# 02 · First PR

The full contract lives in [CONTRIBUTING.md](../../CONTRIBUTING.md); this is the
happy-path loop.

## 1. Branch

Every change starts on a branch off `main`, named for the Linear issue:

```bash
git checkout main && git pull
git checkout -b <type>/ldg-N-<short-slug>      # e.g. feat/ldg-46-transfer-endpoint
```

`<type>` is a Conventional-Commits type: `feat | fix | chore | docs | refactor | test | perf | build | ci`.

## 2. Make the change

Follow the package you're touching — read its `CLAUDE.md` first (e.g.
`src/main/java/com/xidoke/ledger/transfer/CLAUDE.md`). Architecture map:
[ARCHITECTURE.md](../../ARCHITECTURE.md); decisions: [docs/adr/](../adr/).

## 3. Verify locally

```bash
./mvnw spotless:apply              # auto-format (Palantir)
./mvnw verify                      # compile + unit + Testcontainers ITs + ArchUnit + SpotBugs
```

`verify` is the same gate CI runs — green here means green there. (Docker must be up
for the Testcontainers ITs.)

## 4. Commit (Conventional Commits)

```
<type>(<scope>): <imperative summary, ≤ 72 chars>

<body — the WHY, wrapped at 72>

Refs LDG-N        # or "Closes LDG-N" on the change that finishes the issue
```

The commit-msg hook (lefthook) rejects messages that don't match. Don't use
`--no-verify` — fix the message instead.

## 5. Push + open the PR

```bash
git push -u origin <branch>
gh pr create --base main          # fill the PR template; put "Closes LDG-N" in the body
```

The PR template prompts for summary, testing done, and a checklist. CI ("Build, test
& lint") must be green before merge. Merge style is **rebase** onto `main`.

Next: [03 · Troubleshooting](03-troubleshooting.md).
