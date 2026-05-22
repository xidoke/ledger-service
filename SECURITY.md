# Security Policy

## Supported versions

This project is in active development; only the latest published release receives security fixes.

| Version |     Supported      |
|---------|--------------------|
| latest  | :white_check_mark: |
| older   | :x:                |

## Reporting a vulnerability

**Please do not open a public GitHub issue for security vulnerabilities** — that discloses
the issue before a fix exists.

Use **GitHub Private Vulnerability Reporting (PVR)** to report confidentially:

> <https://github.com/xidoke/ledger-service/security/advisories/new>

The report stays visible only to maintainers until coordinated disclosure is complete.

Include: a clear description + impact, steps to reproduce or a proof-of-concept,
affected version(s)/component(s), and any suggested mitigation.

## Response timeline

|          Milestone          |   Target    |
|-----------------------------|-------------|
| Acknowledge the report      | ≤ 72 hours  |
| Severity assessment         | ≤ 7 days    |
| Fix for non-critical issues | ≤ 30 days   |
| Fix for critical issues     | best effort |

Timelines are best-effort for a solo-maintained project and may shift for complex issues.

## Coordinated disclosure (embargo)

A **90-day embargo** (Project Zero standard) applies:

1. Maintainer and reporter agree a disclosure date (default 90 days from acknowledgement).
2. The maintainer publishes an advisory and ships the fix before the embargo expires.
3. If more time is needed, an extension is requested with justification.
4. If the embargo lapses without a fix, the reporter may disclose.

## Out of scope

- Vulnerabilities in third-party dependencies — report those upstream.
- No bug-bounty programme is offered.

## Credit

Reporters are credited (by name or handle) in the published advisory unless they request anonymity.

## PGP

No PGP key is published — GitHub PVR provides a private channel without one.
