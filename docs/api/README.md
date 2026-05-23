# API reference

OpenAPI 3 specification + per-endpoint reference docs.

**Diátaxis**: Reference (neutral, contract-only — no opinions; opinions belong in [`../adr/`](../adr/)).

## The spec

- [`openapi.yaml`](openapi.yaml) — OpenAPI 3 spec auto-generated from the controllers via [SpringDoc](https://springdoc.org/), covering `/accounts`, `/accounts/{id}/topups`, and `/transfers`. The `Idempotency-Key` header requirement and the RFC 7807 (`application/problem+json`) `400`/`409`/`422` error shapes are added by `OpenApiConfig` (they are enforced by a servlet filter + exception handler, not by controller signatures — see [ADR-0012](../adr/0012-idempotency.md)).
- **Live, when the app is running**: `/v3/api-docs` (JSON), `/v3/api-docs.yaml` (YAML), and Swagger UI at `/swagger-ui.html`.

### Regenerating `openapi.yaml`

The committed spec is produced by a test; regenerate it after changing an endpoint:

```bash
./mvnw test -Dtest=OpenApiDocsTest -Dopenapi.dump=true
```

The same test (without the flag) runs in CI and asserts the spec still covers the endpoints, the `Idempotency-Key` header, and the problem shapes.

- Optional per-endpoint deep-dive markdown for complex request/response flows (Phase 2+).

## Convention

- **Spec filename**: `openapi.yaml` (industry standard).
- **Endpoint markdown** (if added): kebab-case nouns or `<resource>-flow.md` for sequence-heavy docs.
- **Neutral tone** — describe what the API does, not why or how to use it for a specific goal. How-to lives in `../onboarding/` or future `docs/how-to/`; rationale lives in `../adr/`.
