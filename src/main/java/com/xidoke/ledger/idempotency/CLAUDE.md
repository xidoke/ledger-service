# idempotency package — CLAUDE.md

Stripe-style `Idempotency-Key` middleware (ADR-0012): a mutating request that carries the header is processed once; retries with the same key replay the stored response instead of re-running the side effect. This is the **client↔server retry-safety** layer — distinct from `@Version` optimistic locking (intra-server conflict, M4) and the outbox (reliable event emit, M5); see `concurrent-api-design/wiki/idempotency-keys-stripe-pattern`.

Structured hexagonally (ADR-0018), but lean — no domain aggregate (it's infra, the ADR-0018 pragmatic exception):

- `domain/` — `IdempotencyRecord` (the stored outcome) + `IdempotencyStore` (outbound port).
- `adapter/out/` — `IdempotencyJdbcStore` over `JdbcClient` (table-level CRUD).
- `adapter/in/` — `IdempotencyFilter` (`OncePerRequestFilter`) + `CachedBodyHttpServletRequest`.

## Filter flow

On `POST` with an `Idempotency-Key` header (header is **optional** in LDG-48): read + cache the body, hash `SHA-256(method + path + body)`, then look up the key:

- **miss** → run the request through a `ContentCachingResponseWrapper`, then store `(key, hash, status, body)` — **only on a 2xx**, so a failed op stays retryable rather than locked behind the key.
- **hit, same hash** → replay the stored status + body, side effect skipped.
- **hit, different hash** → 422 (the key was reused for a different request).

## Gotchas

- A servlet `Filter` runs **before** the DispatcherServlet, so exceptions thrown here bypass `@RestControllerAdvice`. The filter therefore writes the 422 mismatch as `application/problem+json` itself.
- A servlet input stream is single-read; the filter reads the body up front (to hash it) and hands the controller a `CachedBodyHttpServletRequest` that replays the bytes.

## Not here yet

- **Requiring** the header on `/transfers`+`/topups` and the **concurrent same-key in-flight** race (DB unique constraint + `INSERT … ON CONFLICT`/lock) → LDG-49.
- Concurrency IT + **ADR-0012** writeup → LDG-50.
- Key TTL / expiry sweep (Stripe uses 24h) → not scheduled yet; `created_at` is recorded for it.

See repository root `CLAUDE.md` for project-wide conventions.
