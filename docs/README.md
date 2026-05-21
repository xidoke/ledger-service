# ledger-service docs

Documentation organized by purpose. The repo follows a **layered skim path**:

1. [README](../README.md) (5 min) — what + quickstart
2. [ARCHITECTURE.md](../ARCHITECTURE.md) (15 min) — module map + invariants *(Phase 0 LDG-31)*
3. [docs/](.) — deep references, organized by purpose (this folder)
4. [CONTRIBUTING.md](../CONTRIBUTING.md) — conventions for changing code

## Folders

|              Folder              |                          Purpose                           | Diátaxis category |        Status         |
|----------------------------------|------------------------------------------------------------|-------------------|-----------------------|
| [`adr/`](adr/)                   | Architecture Decision Records — what was decided + why     | Explanation       | Phase 0, LDG-16 fills |
| [`architecture/`](architecture/) | C4 diagrams + per-subsystem deep dives                     | Explanation       | Phase 1               |
| [`api/`](api/)                   | OpenAPI spec (auto-gen via SpringDoc) + endpoint docs      | Reference         | Phase 1               |
| [`onboarding/`](onboarding/)     | First-day-to-first-PR walkthrough beyond README Quickstart | Tutorial          | Phase 1               |
| [`runbook/`](runbook/)           | Incident response + operational playbooks                  | How-to (ops)      | Phase 1               |
| [`glossary.md`](glossary.md)     | Domain term dictionary (ubiquitous language)               | Reference         | LDG-30 fills          |

The 4 Diátaxis categories (tutorial / how-to / reference / explanation) are mapped via the table column above rather than as explicit subfolders — the doc set is too small for a 4-folder split to be worth the overhead (see [Diátaxis framework](https://diataxis.fr/)). When the doc count crosses ~20 files we can revisit.

## Naming convention

- **kebab-case** for all filenames (`first-deploy.md`, not `FirstDeploy.md`)
- **ADRs**: `NNNN-title-with-dashes.md` zero-padded (`0001-architectural-style.md`); numbers never reused
- **Onboarding / tutorials**: optional numbered prefix `NN-` for sequential reading order
- **Reference / explanation**: no numbered prefix (no inherent order — consult on demand)
- **All lowercase** enforced — Linux is case-sensitive, macOS is case-insensitive but case-preserving; mixed case breaks CI on Linux

## Index file convention

Each subfolder has a `README.md` so the directory listing renders with context on GitHub. We do not use `index.md` — the repo is plain Markdown, not driven by MkDocs/Docusaurus (deferred).

## Navigation by need

|       If you are...       |                                    Go to                                     |
|---------------------------|------------------------------------------------------------------------------|
| Coding a feature          | [`adr/`](adr/) (why) + [`architecture/`](architecture/) (where)              |
| New to the project        | [`../README.md`](../README.md) Quickstart, then [`onboarding/`](onboarding/) |
| Responding to an incident | [`runbook/`](runbook/)                                                       |
| Looking up an API         | [`api/openapi.yaml`](api/)                                                   |
| Confused by a domain term | [`glossary.md`](glossary.md)                                                 |
| Changing code conventions | [`../CONTRIBUTING.md`](../CONTRIBUTING.md)                                   |

---

*Last updated: Phase 0 (LDG-29). Structure will evolve as Phase 1 fills content.*
