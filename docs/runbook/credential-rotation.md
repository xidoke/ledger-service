# Runbook: credential rotation

> Procedural runbook — the [5-section format](README.md#format--5-sections) maps to a procedure here:
> Triage = scope the secret, Mitigation = the rotation steps, Remediation = follow-ups.

## 1. Summary

Rotate a secret the service consumes — at v0.1 that is the **database password** (`SPRING_DATASOURCE_PASSWORD`). Run this on a routine cadence, or **immediately** on a suspected leak. The mechanism is the same in both cases: every secret reaches the app as an **environment variable** (`SPRING_DATASOURCE_*`, relaxed-bound by Spring Boot — there is no secrets file and no `spring.config.import` in the deploy), so rotating means changing the value in the host env and letting the app rebind on restart.

## 2. Triage (scope the secret)

- Identify which secret and where it is set — at v0.1, the only one is the DB password, set in **Render → the web service → Environment**.
- **Leak vs routine.** A confirmed leak is an incident: rotate now, then audit. Routine rotation can wait for a low-traffic window (rotation triggers a redeploy → brief downtime on the single free instance).
- Confirm no secret is in git history before/after (it never should be — the repo only references `SPRING_DATASOURCE_*` by name):

```bash
git grep -nIE 'postgres(ql)?://[^<]*:[^<@]+@' -- ':!docs/**' || echo "clean: no inline credentials"
```

## 3. Mitigation (rotation procedure)

**Database password:**

1. **Issue the new password** at the source: Render dashboard → the **PostgreSQL** instance → reset/regenerate the password (or, when moving to a new DB such as Neon, this is the new instance's password). Copy it.
2. **Update the env var**: Render → the **web service → Environment** → set `SPRING_DATASOURCE_PASSWORD` to the new value (and `SPRING_DATASOURCE_URL` / `_USERNAME` too if the host or user changed) → **Save changes**.
3. Saving env vars **triggers an automatic redeploy**. The restarted container reads the new env and reconnects — no code change, no commit.

**API keys / other secrets:** none at v0.1 (the outbox poller only logs; no outbound broker or third-party calls yet). When one lands, it follows the identical flow — add it as a `LEDGER_…` / `SPRING_…` env var, rotate by updating the value + redeploy. Never put it in `application*.yml`.

## 4. Validate

```bash
BASE=https://<service>.onrender.com
curl -fsS "$BASE/actuator/health"      # → {"status":"UP",...} once the redeploy completes
```

- Render logs show a clean startup with **no** `PSQLException: password authentication failed`.
- The old password no longer authenticates (the DB provider invalidates it on reset). For a leak, confirm this explicitly.

## 5. Remediation (follow-ups)

- **On a confirmed leak**, also: review DB access logs for unauthorised connections during the exposure window, and rotate any other secret that shared the exposure path.
- **Reduce blast radius** (Phase 2+): move secrets to a managed store (Render secret files / Vault) and shorten the rotation cadence; consider read-only DB roles for the read endpoints.
- **Downtime note:** an env-var change redeploys the single free instance, so rotation causes a short outage. Acceptable at v0.1; revisit once there is more than one instance (zero-downtime rotation needs overlapping-valid credentials).
