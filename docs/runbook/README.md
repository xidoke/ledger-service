# Operational runbooks

Incident response + production playbooks. **Imperative mood, minimal prose.** Written for the "3 AM on-call" scenario: what do I do in the next 5 minutes?

**Diátaxis**: How-to (operations flavor — on-call audience, post-incident update cadence). **Status**: the two Phase-1 runbooks below exist; the rest land Phase 2+ when production-like deploy and monitoring exist.

## Content

|                        File                        |  Phase  |            Trigger            |
|----------------------------------------------------|---------|-------------------------------|
| [`first-deploy.md`](first-deploy.md)               | Phase 1 | Initial v0.1 deploy procedure |
| [`credential-rotation.md`](credential-rotation.md) | Phase 1 | Routine secret rotation       |
| `db-restore.md`                                    | Phase 2 | Restore from backup           |
| `high-error-rate.md`                               | Phase 3 | When monitor alarm fires      |

## Format — 5 sections

Each runbook follows this structure (matches industry convention):

1. **Summary** — one-paragraph what triggered the runbook.
2. **Triage** — quick checks to confirm the situation + estimate severity.
3. **Mitigation** — minimum steps to stop the bleeding (restore service).
4. **Validate** — how to confirm the mitigation worked.
5. **Remediation** — longer-term fix, follow-up tickets, post-mortem link.

## Convention

- **kebab-case** filenames, no numbered prefix (incident-driven, not sequential).
- **Imperative voice** — "Stop the consumer", not "the consumer should be stopped".
- **Concrete commands** — paste the exact `kubectl` / `psql` / `curl` line; do not paraphrase.
- **Link to dashboards / alerts** by URL, not screenshot.
- **Post-incident update** — every runbook gets a note added after each real use (what worked, what was missing).
