---

status: Accepted
date: 2026-05-23
----------------

# ADR-0031: Identifier Strategy — UUID app-generated cho mọi id

## Status

Accepted (2026-05-23). Ported tới repo 2026-05-23 từ vault source `Research/ledger-system-ADR/wiki/adr-031-identifier-strategy.md`. Ratify hóa lựa chọn id đã làm implicit ở LDG-40 (domain typed `AccountId`/`TransactionId` wrap UUID) và LDG-41 (PK `UUID`).

## Context

Roadmap ([ADR-0001](0001-architectural-style.md)) tiến hóa monolith → tách microservice + Kafka ở Phase 2; [ADR-0010](0010-aggregate-boundary.md) chọn Account = đơn vị tách service. Nghĩa là **id phải sống được khi mỗi service có DB riêng** — không giả định một DB trung tâm cấp id. Đổi kiểu id sau khi đã có dữ liệu + đã tách service là cực đắt → phải chọn đúng từ đầu.

## Decision drivers

- **Coordination-free xuyên service** (driver quyết định — nối thẳng với khả năng tách service Phase 2).
- Không cần hạ tầng cấp id riêng ở Phase 0-1.
- Index/write performance (id là PK).
- Không leak volume / chống enumeration.
- Type-safe, khớp Value Object id ([ADR-0019](0019-ddd-tactical-patterns.md)).

## Considered options

### Option A — UUID app-generated (chọn)

Domain sinh UUID; DB cột `UUID`. `ledger_entries.id` để DB sinh (`gen_random_uuid()`) vì entry không có identity domain riêng.

**Pros:** *bản thân là distributed id* — unique toàn cục không cần coordination; tách service Phase 2 không phải đổi id; không hạ tầng cấp id; chống enumeration; id sinh ở domain → test thuần POJO.
**Cons:** 16 byte (gấp đôi BIGINT); **v4 (random)** phân mảnh B-tree index trên PK (page split, giảm write throughput) — khắc phục bằng v7; không sortable (đã có `created_at`).

### Option B — BIGINT auto-increment / sequence

**Cons:** cần coordination từ DB trung tâm; khi mỗi service có DB riêng (Phase 2) sẽ sinh trùng → tái lập đúng bottleneck cần tránh; leak volume + enumeration. Chính là phương án chống lại việc tách service. Loại.

### Option C — Snowflake (64-bit: time + machine-id + sequence)

**Pros:** compact 8 byte; time-sortable; distributed.
**Cons:** cần cấp/quản lý machine-id (coordination + hạ tầng) + xử lý clock skew + tự build generator; phức tạp vận hành không tương xứng scale dự án. Loại hiện tại.

### Option D — UUID v7 (time-ordered, RFC 9562) — biến thể ưu tiên của A

16 byte nhưng prefix theo timestamp → index locality tốt như Snowflake, vẫn coordination-free.
**Cons:** JDK chưa có generator built-in (`UUID.randomUUID()` là v4); cần lib hoặc Hibernate `@UuidGenerator(style = TIME)`; Postgres `uuidv7()` chỉ có từ PG18 (dự án dùng PG17) → sinh v7 ở app-side.

## Decision outcome

Chọn **Option A — UUID application-generated**, **v4 hiện tại**, **v7 là hướng nâng cấp** khi write throughput thành vấn đề. Coordination-free biến việc tách service Phase 2 thành "không phải đổi id". Option B mâu thuẫn driver này; Option C đẻ coordination/hạ tầng chưa cần; v7 là điểm ngọt (giữ coordination-free, lấy lại index locality).

## Consequences

**Positive:** Phase 2 tách service không đổi schema/code id; không hạ tầng cấp id; chống enumeration.
**Negative:** 16 byte/PK; v4 phân mảnh index khi write nhiều (nợ kỹ thuật đã biết, chưa phải vấn đề Phase 0-1).
**Neutral:** không dựa vào tính sortable của id (ordering theo `created_at`); v4 và v7 cùng kiểu cột `uuid` → chuyển dần được, không đổi kiểu cột.

## Risks & open questions

- **Khi nào migrate v4 → v7?** Lý tưởng trước khi dữ liệu lớn; quyết khi tới task repository/persistence. `ledger_entries` (write-heavy) là ứng viên số 1.
- `gen_random_uuid()` PG17 là v4; v7 cần app-side hoặc nâng PG18 (`uuidv7()` native).
- **Snowflake** chỉ cân nhắc nếu index-size 16-byte là bottleneck đo được + chấp nhận worker-id infra — không nằm trong dự kiến.

## References

- [ADR-0001](0001-architectural-style.md) — monolith → service split (lý do cần distributed-ready id)
- [ADR-0010](0010-aggregate-boundary.md) — Account = đơn vị tách service
- [ADR-0019](0019-ddd-tactical-patterns.md) — typed Value Object id (wrap UUID)
- Source analysis: `Research/ledger-system-ADR/wiki/adr-031-identifier-strategy.md`
- RFC 9562 (2024) — UUID v7 time-ordered; Twitter Snowflake
