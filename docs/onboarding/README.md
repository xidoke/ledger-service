# Developer onboarding

First-day-to-first-PR walkthrough. Goes beyond the [README Quickstart](../../README.md#quickstart) (which is happy-path only) by covering edge cases, environment debugging, and team conventions.

**Diátaxis**: Tutorial (learning by doing — beginner state). **Status**: filled (LDG-71). Read in order:

## Contents

1. [`00-prerequisites.md`](00-prerequisites.md) — JDK 21, Docker, IDE choice
2. [`01-first-run.md`](01-first-run.md) — clone → boot → create an account → top-up → see entries
3. [`02-first-pr.md`](02-first-pr.md) — branch, verify, commit format, open PR
4. [`03-troubleshooting.md`](03-troubleshooting.md) — common first-run errors + fixes

## Convention

- **kebab-case** filenames with **optional numbered prefix** `NN-` for sequential reading order.
- **Tutorial tone**: "you" / "first, do X. now, do Y." — direct, guiding, minimal theory (link to `../adr/` for the why).
- **Every step must work reliably** — broken tutorials destroy confidence permanently.
- **Refresh quarterly** via fresh-contributor test: give onboarding to a real new dev, time it, catch every gap.

## References

- [Diátaxis: Tutorials](https://diataxis.fr/tutorials/)
- Project [CONTRIBUTING.md](../../CONTRIBUTING.md) for conventions onboarding refers to
