# idempotency package — CLAUDE.md

Stripe-style `Idempotency-Key` middleware (ADR-0012): a mutating request that carries the header is processed once; retries with the same key replay the stored response instead of re-running the side effect. This is the **client↔server retry-safety** layer — distinct from `@Version` optimistic locking (intra-server conflict, M4) and the outbox (reliable event emit, M5); see `concurrent-api-design/wiki/idempotency-keys-stripe-pattern`.

Structured hexagonally (ADR-0018), but lean — no domain aggregate (it's infra, the ADR-0018 pragmatic exception):

- `domain/` — `IdempotencyRecord` (the stored outcome) + `IdempotencyStatus` (PENDING/COMPLETED) + `IdempotencyStore` (outbound port).
- `adapter/out/` — `IdempotencyJdbcStore` over `JdbcClient`.
- `adapter/in/` — `IdempotencyFilter` (`OncePerRequestFilter`) + `CachedBodyHttpServletRequest`.

## Filter flow (claim-first)

On a `POST` carrying `Idempotency-Key`: read + cache the body, hash `SHA-256(method + path + body)`, then **claim the key** with `INSERT … ON CONFLICT (key) DO NOTHING` (writes a PENDING row) — this atomic insert serializes concurrent same-key requests at the DB:

- **claim won** → run through a `ContentCachingResponseWrapper`; on a **2xx** store the response + mark COMPLETED; on a **non-2xx or a throw** `release` (delete) the PENDING row so the operation stays retryable.
- **claim lost, COMPLETED, same hash** → replay the stored status + body, side effect skipped.
- **claim lost, COMPLETED, different hash** → **422** (key reused for a different request — kept distinct from 409).
- **claim lost, still PENDING** → **409** (a concurrent request with this key is in flight).

The header is **required** on the money endpoints (`/transfers`, `/accounts/*/topups`) — missing there is **400**; on other POSTs it is optional (claim-first applies only when a key is supplied).

## Gotchas

- A servlet `Filter` runs **before** the DispatcherServlet, so exceptions thrown here bypass `@RestControllerAdvice`. The filter therefore writes 400/409/422 as `application/problem+json` itself.
- A servlet input stream is single-read; the filter reads the body up front (to hash it) and hands the controller a `CachedBodyHttpServletRequest` that replays the bytes.
- Claim + complete are **separate commits** on purpose: the PENDING row must be visible to a concurrent request, so it commits before the operation runs.

## Not here yet

- **Concurrent-threads IT + ADR-0012** writeup → LDG-50.
- **Orphaned PENDING on crash**: if the process dies mid-flight, the PENDING row lingers → permanent 409 for that key until a TTL/reaper sweeps it. No reaper yet (`created_at` is recorded for it) — follow-up.

See repository root `CLAUDE.md` for project-wide conventions.
