# 01 · First run

Goal: clone, boot the service, and move some money — end to end — in a few minutes.

## 1. Clone + boot

```bash
git clone <repo-url> ledger-service
cd ledger-service

docker info > /dev/null            # Docker must be up (see 00-prerequisites)
./mvnw spring-boot:run             # Spring Boot auto-starts the postgres:17 container
```

First run pulls dependencies + the Postgres image (~5 min); later runs take ~10 s.

Proof of life, in another shell:

```bash
curl http://localhost:8080/hello
# → {"message":"ledger-service up"}
```

## 2. Create an account

```bash
curl -s -XPOST http://localhost:8080/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerRef":"alice","currency":"USD"}'
# → 201 {"id":"<uuid>", ... ,"balanceMinorUnits":0}
```

Grab the `id` — call it `$ACC`.

## 3. Top up (note the Idempotency-Key)

The money endpoints (`/topups`, `/transfers`) **require** an `Idempotency-Key`
header — a retry with the same key replays instead of double-charging.

```bash
curl -s -XPOST http://localhost:8080/accounts/$ACC/topups \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amountMinorUnits":1000}'
# → 201 {"transactionId":"...","balanceMinorUnits":1000,"currency":"USD"}
```

Send the **same** request with the **same** key → you get the same response back and
the balance stays `1000` (replayed, not re-run). Omit the key entirely → `400`.

## 4. See the entries

```bash
curl -s http://localhost:8080/accounts/$ACC/entries   # ledger-entry history, newest first
```

Amounts are integer **minor units** (1000 = $10.00) everywhere — never floats.

## Alternative boot modes

```bash
./mvnw spring-boot:test-run                       # ephemeral DB (Testcontainers), resets each restart
docker compose -f compose.app.yaml up --build     # app + Postgres both in containers
```

Next: [02 · First PR](02-first-pr.md).
