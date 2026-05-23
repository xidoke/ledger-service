# 03 · Troubleshooting

Common first-run snags and the fix. If you hit one that isn't here, add it.

## `Could not connect to Docker` / app hangs on startup

The app auto-starts a Postgres container, so Docker must be running.

```bash
docker info > /dev/null && echo ok    # if this fails, start Docker Desktop / Colima / OrbStack
```

The Testcontainers integration tests need it too — a stopped Docker daemon shows up
as ITs failing to start a container.

## `Port 8080 already in use` (or `5432`)

Something else holds the port (a previous run, another app).

```bash
lsof -i :8080     # find the PID, then kill it
```

Postgres maps to a random host port via the compose file, so `5432` clashes are rare;
if you run a local Postgres on 5432 it won't conflict with the containerised one.

## `mvnw: permission denied` / wrong Java

```bash
chmod +x mvnw            # if needed after a fresh clone
./mvnw -version          # must report Java 21 — see 00-prerequisites if not
```

Always use `./mvnw`, never a system `mvn` (the wrapper pins the Maven version).

## `spotless:check` fails in CI but the code looks fine

Formatting drift. Run the formatter and recommit:

```bash
./mvnw spotless:apply
```

## Commit rejected by the hook

The commit-msg hook enforces Conventional Commits. Re-word the message (`<type>(<scope>): …`)
— don't bypass with `--no-verify`. If the hooks aren't running at all, install them:
`brew install lefthook && lefthook install`.

## Flyway: `Validate failed` / migration checksum mismatch

Applied migrations are **immutable** — never edit a `V<n>__*.sql` that has run. Write a
new `V<n+1>__*.sql` instead. For a clean local slate, drop the dev database volume
(`docker compose down -v`) and let Flyway re-migrate from scratch.

## A money endpoint returns `400 Missing Idempotency-Key`

`/transfers` and `/accounts/{id}/topups` require an `Idempotency-Key` header — send a
fresh UUID (`-H "Idempotency-Key: $(uuidgen)"`). See [01 · First run](01-first-run.md).
