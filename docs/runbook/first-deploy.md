# Runbook: first deploy (v0.1 → Render)

> Procedural runbook — the [5-section format](README.md#format--5-sections) maps to a procedure here:
> Triage = pre-flight, Mitigation = the deploy steps, Remediation = rollback + follow-ups.
> Documents the **actual** v0.1 deploy (LDG-60), not an invented one.

## 1. Summary

Stand up ledger-service v0.1 on a fresh **Render** Docker web service backed by a managed PostgreSQL. One-time bootstrap; after this, Render auto-deploys every push to `main`. Secrets are supplied as env vars (`SPRING_DATASOURCE_*`) and never committed.

## 2. Triage (pre-flight checks)

Confirm before touching Render:

```bash
# main is green (the image builds from main)
gh pr checks main 2>/dev/null || gh run list -L 1
# prod profile + container are present
ls src/main/resources/application-prod.yml Dockerfile
# the image builds locally (optional but cheap insurance)
docker build -t ledger-service:preflight .
```

- `application-prod.yml` binds Render's injected `PORT` (`server.port: ${PORT:8080}`) — without it the app binds 8080 and Render reports "no open ports detected".
- You have a Render account and the GitHub repo is connected.

## 3. Mitigation (the deploy procedure)

**3a. Create the database.** Render dashboard → **New → PostgreSQL** → Free plan → pick a region (note it). After it provisions, open **Connections** and copy the **Internal** connection fields (host, database, user, password) — internal is same-region and avoids the public network.

**3b. Create the web service.** **New → Web Service** → connect the repo → set:

|      Setting      |                          Value                           |
|-------------------|----------------------------------------------------------|
| Runtime           | **Docker**                                               |
| Region            | **same as the database** (so the internal host resolves) |
| Instance type     | **Free**                                                 |
| Health Check Path | `/actuator/health`                                       |
| Branch            | `main`                                                   |

**3c. Set environment variables** (Environment tab — these are the only place the secret lives):

```
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://<internal-host>/<db-name>
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password from the Render Postgres page>
```

> Prefix the host with `jdbc:postgresql://` — Render's copy button gives a bare `postgresql://…` URL that the JDBC driver rejects.

**3d. Create Web Service.** Render builds the multi-stage Docker image, then starts the container. On startup the app binds `$PORT`, connects with the env credentials, and **Flyway runs migrations V1–V9** against the fresh database (creating the schema + seeding `SYSTEM_FUNDING`).

## 4. Validate

```bash
BASE=https://<service>.onrender.com   # e.g. https://ledger-service-bjzr.onrender.com

# health is UP
curl -fsS "$BASE/actuator/health"        # → {"status":"UP",...}

# end-to-end smoke: schema migrated + double-entry posting works on prod
ACC=$(curl -fsS -X POST "$BASE/accounts" -H 'Content-Type: application/json' \
  -d '{"ownerRef":"smoke","currency":"USD"}')
ID=$(echo "$ACC" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
curl -fsS -X POST "$BASE/accounts/$ID/topups" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -d '{"amountMinorUnits":500}'   # → balance 500
curl -fsS "$BASE/accounts/$ID/entries"     # → one CREDIT 500 entry
```

In the Render logs, confirm Flyway reported `Successfully applied N migrations` and there are no `Connection refused` / auth errors.

## 5. Remediation (rollback + follow-ups)

**Rollback.** Render **Deploys** tab → pick the last-good deploy → **Rollback**. The *first* deploy has no prior, so recovery is fix-forward: correct the issue, push to `main`, let Render redeploy.

**Known, expected behaviour (not bugs):**

- **Cold start** — the free instance spins down after ~15 min idle; the first request then takes ~50 s. A request landing mid-spin-up can briefly return a Cloudflare `404` with header `x-render-routing: no-server`. Retry once the instance is warm.

**Follow-ups:**

- **Free Postgres expires 30 days after creation.** Diarise the expiry; before it lapses, migrate to a persistent DB (e.g. Neon) by re-pointing `SPRING_DATASOURCE_*` — see [credential-rotation.md](credential-rotation.md).
- Free tier has no automated backups — `db-restore.md` (Phase 2) assumes a paid plan.
