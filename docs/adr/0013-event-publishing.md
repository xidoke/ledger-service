---

status: Accepted
date: 2026-05-20
----------------

# ADR-0013: Event Publishing — Transactional Outbox

## Status

Accepted (2026-05-20). Distilled tới repo 2026-05-24 từ vault source `Research/ledger-system-ADR/wiki/adr-013-event-publishing.md`. **Write side đã land (LDG-54)**; poller (read side) = LDG-55 — xem §Trạng thái triển khai.

## Context

Khi một transfer/topup commit vào ledger, component khác cần biết — reconciliation, (tương lai) notification, fraud. Naive: sau khi DB commit, publish thẳng lên downstream/broker. Đây là **dual-write** — hai I/O (DB + broker) cần cùng succeed/fail mà không có transaction bao phủ cả hai.

Failure mode khắc nghiệt nhất với tiền: DB commit OK nhưng publish fail → ledger đã ghi tiền nhưng không ai biết; hoặc publish OK nhưng DB rollback → downstream hành động trên phantom state. Cả hai không chấp nhận được (xem [[dual-write-problem]], [[event-store-dual-write]]). Ledger ở Nấc 0-1 chưa có Kafka, nhưng phải thiết kế đúng từ đầu để Nấc 2+ không refactor.

## Decision drivers

- **Atomicity ledger-state ↔ event là bắt buộc** — không split-brain dù xác suất thấp.
- **Chưa có broker ở Nấc 0-1** — infra nặng chưa justified; poller log/gọi nội bộ là đủ verify flow.
- **Mở đường nâng cấp** — swap publish target (log → Kafka → CDC) không đụng application layer ghi outbox.
- **Vận hành đơn giản** — không thêm Debezium/Kafka/ZooKeeper ở Nấc 0-1.
- **Consumer idempotency là contract** — poller at-least-once nên consumer phải xử lý duplicate.

## Considered options

### Option A — Transactional outbox + polling poller (CHỌN)

Trong cùng `@Transactional` với business logic, INSERT thêm một row `outbox` (PENDING). Rollback vì bất kỳ lý do gì (optimistic-lock conflict, constraint, timeout) → cả ledger entry lẫn outbox row đều không tồn tại (ACID). Một poller riêng (`@Scheduled`) đọc `PENDING ORDER BY id`, publish, rồi mark `SENT` + `published_at`.

**Pros:** atomicity tuyệt đối; zero infra mới (PostgreSQL + Spring scheduler); swap publish target không đụng app; dễ monitor (`count(*) WHERE PENDING AND created_at < now()-5m`); outbox table là audit trail trực tiếp.
**Cons:** cần purge SENT rows; polling latency (~1s, không realtime); poller là component cần monitor; **at-least-once** → consumer phải idempotent.

### Option B — Publish trực tiếp trong/sau transaction (dual-write anti-pattern)

**Cons:** publish fail sau commit → event mất vĩnh viễn (silent); publish trước rollback → phantom event; không retry tự động. Với tiền: loại.

### Option C — CDC qua Debezium (đọc WAL)

**Pros:** không đụng app code; latency thấp; không cần outbox table.
**Cons:** infra nặng (Kafka + Debezium + `wal_level=logical`) — overkill Nấc 0-1; operational complexity cao; schema coupling; debug khó. Loại ở Nấc 0-1, để ngỏ cho Nấc 2+.

## Decision outcome

**Chọn Option A.** Atomicity ledger↔event không compromise được với tiền; Option B vi phạm từ design; Option C đủ atomic nhưng infra không justify ở Nấc 0-1. Option A đúng ngữ nghĩa, zero infra mới, upgrade path rõ. Nấc 0: poller log; Nấc 1: gọi component nội bộ (reconciliation); Nấc 2+: swap sang Kafka/CDC, app ghi outbox không đổi.

## Trạng thái triển khai (2026-05-24)

- **Write side (LDG-54, đã land):** `outbox` table (Flyway V8) — `id BIGINT IDENTITY` (monotonic = thứ tự delivery cho poller; chọn IDENTITY thay vì ULID vì single-instance Postgres, đơn giản + strict-order), `aggregate_id` (= transaction id), `event_type`, `payload JSONB`, `status PENDING/SENT`, `schema_version`, `created_at`, `published_at`, partial index `(id) WHERE PENDING`. `TransferService`/`TopupService` gọi `OutboxRepository.append(...)` (port `outbox/domain`, impl JdbcClient `outbox/adapter/out`) **trong cùng `@Transactional`** sau khi ghi ledger → atomic. Event: `TransferPosted` / `TopupPosted` (scalar payload, contract ổn định cho consumer). ArchUnit cho phép use-case → outbox (emit), cấm chiều ngược.
- **Read side (LDG-55, đã land):** `OutboxPoller` (`@Scheduled @Transactional`) đọc PENDING `ORDER BY id … FOR UPDATE SKIP LOCKED` (multi-poller-safe) → publish → `markSent`+`published_at`; row publish-fail giữ PENDING (retry tick sau). Nấc 0 chưa có broker nên "publish" = giao cho `LoggingIdempotentConsumer` (in-process) — **idempotent-consumer skeleton**: dedup theo `outbox.id` (stable publisher id) qua inbox `processed_events` (Flyway V9, `INSERT … ON CONFLICT DO NOTHING` = claim atomic, tránh check-then-act race). At-least-once → duplicate redelivery chạy side-effect đúng 1 lần. Phase 2 swap consumer sang broker thật, giữ nguyên shape dedup-then-act.

## Consequences

**Positive:** không thể ledger-commit-mà-không-event hay event-mà-rollback; upgrade path rõ; outbox = audit trail dễ debug + monitor 1 query.
**Negative:** cần purge job SENT rows (retention); at-least-once → mọi consumer phải idempotent; polling latency; poller cần lifecycle management.
**Neutral:** payload JSON/JSONB Nấc 0-1; Nấc 2+ Kafka cần quyết serialization (JSON vs Avro/Protobuf).

## Risks & open questions

- **Stuck PENDING** (downstream down) → outbox tích lũy; cần alert khi PENDING count/age vượt threshold (poller = LDG-55).
- **Bloat** → cleanup SENT rows theo retention (follow-up khi poller land).
- **Ordering**: poller đọc `ORDER BY id`; `id` IDENTITY monotonic = thứ tự insert.
- **Consumer idempotency**: contract cứng — review checklist khi viết consumer đầu tiên (LDG-55+).

## References

- [ADR-0006](0006-balance-representation.md) — balance cache + entries commit cùng transaction (outbox row tham gia cùng transaction này)
- [ADR-0011](0011-concurrency-strategy.md) — optimistic-lock retry: retry replay cả outbox append (idempotent vì cùng transaction)
- [ADR-0012](0012-idempotency.md) — exactly-once phía client; outbox là at-least-once phía publish
- Source analysis: `Research/ledger-system-ADR/wiki/adr-013-event-publishing.md`; pattern: `Research/messaging-fundamentals/wiki/outbox-pattern.md`
