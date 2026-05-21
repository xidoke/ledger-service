# Developer onboarding

First-day-to-first-PR walkthrough. Goes beyond the [README Quickstart](../../README.md#quickstart) (which is happy-path only) by covering edge cases, environment debugging, and team conventions.

**Diátaxis**: Tutorial (learning by doing — beginner state). **Status**: scaffold; Phase 1 fills as the project shapes up.

## Planned content (Phase 1)

- `00-prerequisites.md` — JDK 21 install, Docker setup, IDE choice
- `01-first-run.md` — clone → `docker compose up` → verify `/hello`
- `02-first-pr.md` — branch, commit format, run tests, open PR
- `03-troubleshooting.md` — common errors + fixes

## Convention

- **kebab-case** filenames with **optional numbered prefix** `NN-` for sequential reading order.
- **Tutorial tone**: "you" / "first, do X. now, do Y." — direct, guiding, minimal theory (link to `../adr/` for the why).
- **Every step must work reliably** — broken tutorials destroy confidence permanently.
- **Refresh quarterly** via fresh-contributor test: give onboarding to a real new dev, time it, catch every gap.

## References

- [Diátaxis: Tutorials](https://diataxis.fr/tutorials/)
- Project [CONTRIBUTING.md](../../CONTRIBUTING.md) for conventions onboarding refers to
