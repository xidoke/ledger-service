# API reference

OpenAPI 3 specification + per-endpoint reference docs.

**Diátaxis**: Reference (neutral, contract-only — no opinions; opinions belong in [`../adr/`](../adr/)). **Status**: scaffold; Phase 1 generates `openapi.yaml` via SpringDoc when domain endpoints land. Phase 0 endpoint set: `/hello` + `/actuator/health` only (skeleton).

## Planned content (Phase 1)

- `openapi.yaml` — auto-generated from controller annotations via [SpringDoc](https://springdoc.org/).
- Optional per-endpoint deep-dive markdown for complex request/response flows (Phase 2+).

## Convention

- **Spec filename**: `openapi.yaml` (industry standard).
- **Endpoint markdown** (if added): kebab-case nouns or `<resource>-flow.md` for sequence-heavy docs.
- **Neutral tone** — describe what the API does, not why or how to use it for a specific goal. How-to lives in `../onboarding/` or future `docs/how-to/`; rationale lives in `../adr/`.
