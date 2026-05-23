---

status: Accepted
date: 2026-05-20
----------------

# ADR-0011: Chiến lược Concurrency — Optimistic Locking (`@Version`) + bounded retry

## Status

Accepted (2026-05-20). Distilled tới repo 2026-05-24 từ vault source `Research/ledger-system-ADR/wiki/adr-011-concurrency-strategy.md`, bổ sung **số đo thật** (LDG-53) và phản ánh cơ chế đã land (LDG-52). Cơ chế retry được implement ở LDG-52; xem §Cập nhật triển khai.

## Context

Nhiều client có thể gửi request đồng thời vào cùng một account: hai top-up song song, một top-up + một transfer, hoặc nhiều transfer rút từ một ví phổ biến. Không có concurrency control là **read-modify-write race** kinh điển — hai request cùng đọc balance, cùng thấy "đủ tiền", cùng ghi → overdraft / lost update. `READ COMMITTED` (mặc định PostgreSQL) **không** chặn dạng race này.

Bảng `accounts` đã có cột `version BIGINT` (ADR-0010, Account là aggregate/locking boundary). Transaction ledger ngắn (2 INSERT `ledger_entries` + 1 UPDATE `accounts` cache, ADR-0006), nên giữ lock lâu là không cần thiết. Dự án ở Nấc 0-1: read-heavy (balance/history đọc nhiều hơn ghi nhiều lần), ưu tiên correctness + đơn giản vận hành.

## Decision drivers

- **Correctness tuyệt đối với tiền** — phải loại bỏ hoàn toàn lost update, không chỉ giảm xác suất.
- **Read-heavy** — reads (balance, history, report) không nên bị block bởi writes.
- **Transaction ngắn** — lock chỉ cần tồn tại ở micro-giây lúc commit.
- **Đơn giản vận hành Nấc 0-1** — không thêm Redis/coordinator khi chưa cần.
- **Mở đường nâng cấp** — hot-account mitigation (async/shard) để dành ADR sau, không lock vào một implementation.

## Considered options

### Option A — Optimistic locking `@Version` + bounded retry (CHỌN)

Hibernate sinh `UPDATE accounts SET …, version = version + 1 WHERE id = ? AND version = ?`; kẻ thua (0 row) nhận `OptimisticLockingFailureException` lúc commit. Controller retry trong transaction mới (reload ở version hiện tại), capped + backoff + jitter; cạn attempt → 409.

**Pros:** zero infra mới (JPA built-in); reads không bị block; lock chỉ ở micro-giây commit; deadlock không thể xảy ra; compose tự nhiên với idempotency (ADR-0012) cho retry an toàn.
**Cons:** hot account → retry storm (xem Risks); developer phải catch đúng exception + retry phải idempotent.

### Option B — Pessimistic locking `SELECT … FOR UPDATE`

Lock row trước khi update; writer-2 đợi writer-1 commit.

**Pros:** không cần retry; serialize per-row dễ lý luận; **dưới hot-row contention nhanh hơn** (xem benchmark).
**Cons:** reads phải đợi lock release (giết read-heavy profile); deadlock risk (A→B vs B→A — phải lock theo thứ tự id, convention dễ vi phạm); giữ lock suốt transaction → bất kỳ latency nào cũng nghẹt writer khác.

### Option C — Redis distributed lock

`SET key NX PX ttl` trước khi update.

**Pros:** tránh cả deadlock lẫn retry storm; granularity per-account.
**Cons:** thêm infra (Redis) chỉ để giải việc JPA đã làm được ở Nấc 0-1; Redis down → chặn toàn write path (SPOF mới); TTL khó tune (ngắn → expire trước commit; dài → process chết giữ lock lâu).

## Decision outcome

**Chọn Option A.** Nấc 0-1 chưa có hot account thực sự; profile read-heavy hưởng lợi lớn nhất từ "reads không bao giờ block". Zero infra mới, deadlock-free. Retry mặc định: capped 5 attempts, backoff 25–200 ms full jitter, cạn → 409 (xem ADR-0018 status taxonomy).

## Benchmark (đo thật — LDG-53)

`TransferConcurrencyBenchmark` (tag `benchmark`, ngoài CI; chạy `./mvnw test -Pbenchmark`) so **cùng một `TransferService` body**, chỉ khác chiến lược lock — optimistic+retry (path production) vs pessimistic bọc `SELECT … FOR UPDATE` (lock theo id-order). 50 writer đồng thời, đo ở tầng service.

|                        Kịch bản                        |                     Optimistic + retry                     | Pessimistic `FOR UPDATE` |
|--------------------------------------------------------|------------------------------------------------------------|--------------------------|
| **Low-contention** (50 cặp account rời)                | **34 ms** · 50/50 ok · 50 attempts (**0 retry waste**)     | 31 ms · 50/50 ok         |
| **High-contention** (50 transfer rút từ **1 hot row**) | **731 ms** · 50/50 ok · 235 attempts (**185 retry waste**) | **358 ms** · 50/50 ok    |

**Đọc số:**

- **Low-contention (case phổ biến): ngang nhau** (34 vs 31 ms) và optimistic **0 retry waste** — không trả giá gì, lại không block reads. Đây là lý do cốt lõi giữ optimistic.
- **High-contention (hot row): pessimistic nhanh ~2×** (358 vs 731 ms) và 0 lãng phí; optimistic đốt **185 attempt thừa** (235/50 ≈ 4.7× công) vì collision + backoff sleep. Nhưng pessimistic thắng ở đây bằng cách **serialize + block reads** — đúng thứ ta không muốn cho read-heavy — và **không** giải được hot-account thật (chỉ xếp hàng). Lời giải hot-account là async/shard (xem Risks), không phải đổi sang pessimistic toàn cục.

→ Quyết định optimistic+retry **không đổi**. Giá trị của số: định lượng **chi phí** của lựa chọn dưới contention (retry waste) → cho biết **ngưỡng** để kích hoạt async/shard khi hot account xuất hiện.

> Methodology: 1 PostgreSQL 17 (Testcontainers) local, service-level (bỏ HTTP + idempotency filter để cô lập chi phí concurrency-control), `max-attempts` nâng lên 200 để cả hai hoàn tất đủ 50 transfer (so equal-work), Hikari pool 64. Single run — số minh hoạ **bậc độ lớn**, không phải benchmark sản xuất (chưa có k6/Nấc 3-4).

## Consequences

**Positive:** reads tự do song song với writes; zero infra dependency Nấc 0-1; deadlock không thể xảy ra; cột `version` thêm vai trò audit (số transition của account).
**Negative:** retry overhead khi contention cao (định lượng ở trên); service phải retry đúng + compose idempotency để tránh duplicate.
**Neutral:** cần `@Transactional` đúng để JPA flush + exception propagate; retry helper nằm *ngoài* `@Transactional` (mỗi attempt = transaction mới).

## Risks & open questions

- **Hot account ở Nấc 2+**: `SYSTEM_FUNDING` bị mọi top-up debit → hot-account cấu trúc; retry rate sẽ bùng (benchmark high-contention là bản thu nhỏ). Mitigation: async queue / sub-account shard — ADR riêng khi đo được ngưỡng. Monitor retry rate per account làm signal. Xem `Research/ledger-systems/wiki/system-funding-account-design.md`.
- **Retry + idempotency interplay**: retry phải check `Idempotency-Key` trước khi ghi entries (ADR-0012) — nếu lần đầu đã commit, retry trả cached response, không tạo duplicate.

## References

- [ADR-0006](0006-balance-representation.md) — balance cache commit cùng transaction với entries
- [ADR-0010](0010-aggregate-boundary.md) — Account là aggregate / locking boundary (`version` ở đây)
- [ADR-0012](0012-idempotency.md) — optimistic-lock retry dùng cùng `Idempotency-Key`
- [ADR-0018](0018-hexagonal-architecture.md) — error → HTTP status taxonomy (409 cho conflict)
- Source analysis + tradeoff sâu: `Research/ledger-system-ADR/wiki/adr-011-concurrency-strategy.md`
- `Research/ledger-systems/wiki/optimistic-locking-for-ledgers.md`, `lost-update-on-balance.md`, `hot-account-problem.md`
