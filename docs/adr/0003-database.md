---

status: Accepted
date: 2026-05-20
----------------

# ADR-0003: Database — PostgreSQL

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-22 từ vault source `Research/ledger-system-ADR/wiki/adr-003-database.md`.

## Context

Ledger Service cần một storage layer phục vụ hai yêu cầu cốt lõi của bất kỳ hệ thống tài chính nào: (1) đảm bảo tính nhất quán tuyệt đối của giao dịch và (2) lưu trữ dữ liệu tiền tệ chính xác không mất mát.

Bối cảnh từ [ADR-0001](0001-architectural-style.md): không dùng full event sourcing, không cần purpose-built event store. Dữ liệu tài chính sống trong RDBMS thông thường với `ledger_entries` là bảng append-only và `accounts.balance` là cache được cập nhật trong cùng một ACID transaction.

Ràng buộc: Phase 0-1 chạy Docker Compose, miễn phí hoặc chi phí thấp. Phase 2 có thể deploy lên cloud managed service.

## Decision drivers

**1. ACID với isolation level mạnh.** Giao dịch tài chính đòi hỏi `SERIALIZABLE` hoặc `REPEATABLE READ` isolation để ngăn phantom read và lost update. Lost-update-on-balance chỉ rõ: hai request đồng thời đọc cùng balance rồi ghi đè nhau dẫn đến overdraft. RDBMS với proper isolation là foundation, optimistic locking ([ADR-0011](0011-concurrency-strategy.md), Phase 1) là tầng bổ sung trên đó.

**2. `NUMERIC` type cho tiền tệ.** Money representation chỉ rõ: `FLOAT` hay `DOUBLE PRECISION` tích lũy floating-point error không chấp nhận được trong tài chính. `NUMERIC(precision, scale)` của PostgreSQL lưu chính xác số thập phân, không làm tròn, không mất bit. Đây là hard requirement. (Lưu ý: [ADR-0007](0007-money-representation.md) chọn BIGINT integer minor units thay vì NUMERIC — vẫn fit với PostgreSQL.)

**3. JSONB cho outbox payload.** Outbox table ([ADR-0013](0013-event-publishing.md), Phase 1) cần lưu payload sự kiện dưới dạng flexible JSON. PostgreSQL `JSONB` là binary-encoded JSON với index support — tốt hơn nhiều so với `TEXT` column chứa JSON string, và tốt hơn `JSON` column vì JSONB được parse và store một lần.

**4. `SELECT FOR UPDATE` và row-level locking.** Dù optimistic locking là default strategy, có những scenario cụ thể (ví dụ: kiểm tra balance và insert entry trong cùng một critical section) có thể cần pessimistic locking. PostgreSQL `SELECT FOR UPDATE` với MVCC cho phép đọc không block và ghi có lock — không làm chậm read-heavy workload.

**5. Mature ecosystem + Spring Data JPA integration.** Với Java 21 + Spring Boot 3 ([ADR-0002](0002-language-framework.md)), Spring Data JPA + Hibernate là ORM layer. PostgreSQL JDBC driver và Hibernate dialect cho PostgreSQL là first-class, được test kỹ lưỡng, nhiều example trong community fintech.

**6. Miễn phí và phổ biến.** PostgreSQL là open-source, không license cost. Cloud managed options (AWS RDS, GCP Cloud SQL, Supabase) đều hỗ trợ PostgreSQL natively.

## Considered options

### Option A: PostgreSQL (LỰA CHỌN)

PostgreSQL 15+ trên Docker Compose (dev) và Cloud SQL / RDS (production tương lai). Dùng qua Spring Data JPA + Hibernate.

**Pros:**

- ACID với SERIALIZABLE isolation — mạnh nhất trong RDBMS thông thường.
- MVCC (Multi-Version Concurrency Control): reads không block writes, writes không block reads — quan trọng cho workload read-heavy của ledger service.
- `NUMERIC(19, 4)` cho tiền tệ, chính xác tuyệt đối (hoặc BIGINT minor units — xem ADR-0007).
- JSONB cho outbox payload với GIN index support.
- `SELECT FOR UPDATE` + `FOR UPDATE SKIP LOCKED` (hữu ích cho outbox poller — chỉ lock rows đang được process, không block toàn bảng).
- Flyway migration support first-class.
- Miễn phí, Docker image chính thức.
- Extensive community và documentation về ledger patterns trên PostgreSQL.

**Cons:**

- Hot account problem vẫn tồn tại: nhiều concurrent writes vào cùng một account row tạo lock contention. Giải pháp: optimistic locking + retry (ADR-0011), không phải database limitation — đây là vấn đề cần giải quyết ở application layer với bất kỳ RDBMS nào.
- Scale horizontal khó hơn NoSQL (nhưng không cần ở Phase 0-1, và PostgreSQL read replicas handle read scale ở Phase 2).
- WAL-based CDC (nếu cần ở Phase 2 cho Debezium) yêu cầu cấu hình `wal_level = logical`.

### Option B: MySQL / MariaDB

MySQL 8.x hoặc MariaDB — lựa chọn RDBMS phổ biến, dùng nhiều trong web development truyền thống.

**Pros:**

- Nhẹ hơn PostgreSQL, startup nhanh hơn trong Docker.
- Cloud managed options: AWS RDS MySQL, PlanetScale (MySQL-compatible).

**Cons:**

- Isolation level mặc định là `REPEATABLE READ` (không phải `SERIALIZABLE`) — cần cấu hình thủ công và hiểu ý nghĩa.
- JSON support yếu hơn PostgreSQL: `JSON` type có, nhưng không có `JSONB` — không có binary encoding, index support hạn chế hơn.
- Không có `SELECT FOR UPDATE SKIP LOCKED` trong MySQL 8.0 trước 8.0.1 — outbox poller implementation phức tạp hơn.
- Decimal type tương đương nhưng cộng đồng ledger-pattern trên PostgreSQL lớn hơn.
- Spring Data JPA + Hibernate dialect cho MySQL có một số gotcha nhỏ (AUTO_INCREMENT vs SEQUENCE cho ID generation).
- **Không có lý do kỹ thuật rõ ràng** để chọn MySQL thay PostgreSQL cho ledger use case.

### Option C: TigerBeetle

Database tài chính chuyên dụng, được thiết kế đặc biệt cho double-entry bookkeeping. Debit/credit là primitive bậc nhất. Append-only. Strong serializability được validate bởi Jepsen.

**Pros:**

- 1 million transfers/second trên commodity hardware với single-threaded serial processing và không có lock contention.
- Debit/credit invariant là database primitive — không cần application-layer enforce.
- Append-only theo thiết kế — không thể `UPDATE` hay `DELETE` transfer đã commit.
- Jepsen-validated: tested dưới process crash, network partition, disk corruption — vượt trội về correctness so với PostgreSQL dưới extreme failures.
- Đây là technology của tương lai cho high-performance ledger.

**Cons:**

- **Learning overhead quá cao cho Phase 0-1**: TigerBeetle có data model riêng (Accounts + Transfers với 6-dimension model), không có ORM, client library ở nhiều ngôn ngữ nhưng Java client còn tương đối non-mature.
- **Two-database pattern bắt buộc**: TigerBeetle không thay thế general-purpose DB — metadata, user profiles, descriptions vẫn phải ở PostgreSQL. Tức là vẫn cần PostgreSQL, cộng thêm TigerBeetle.
- Multi-currency transfers awkward: TigerBeetle enforce "transfer chỉ trong cùng một ledger", multi-currency cần intermediate account — phức tạp hơn cho Phase 0-1.
- **Revisit ở Phase 5+**: khi dự án đã production-scale và cần 100k+ TPS, TigerBeetle là lựa chọn rõ ràng. Không phải bây giờ.

## Decision outcome

**Chọn Option A: PostgreSQL.**

Driver 1 (ACID + SERIALIZABLE isolation) và Driver 2 (`NUMERIC` type) loại bỏ MySQL vì không có lý do thuyết phục để chọn MySQL thay PostgreSQL. Driver 3 (JSONB cho outbox) củng cố thêm PostgreSQL. Driver 5 (Spring Data JPA integration) và Driver 6 (phổ biến + miễn phí) hoàn thiện bức tranh.

TigerBeetle là công nghệ rất ấn tượng và đúng cho bài toán ledger về mặt kỹ thuật. Nhưng learning overhead của nó (data model khác, Java client non-mature, two-database pattern bắt buộc) khiến nó không phải lựa chọn đúng cho giai đoạn này. Revisit at Phase 5+ khi cần 100k+ TPS.

## Consequences

**Positive:**

- `NUMERIC(19, 4)` (hoặc BIGINT minor units per ADR-0007) đảm bảo không mất precision cho mọi tính toán tiền tệ ở scale Phase 0-1.
- MVCC cho phép reads không bị block bởi concurrent writes — ledger service có nhiều reads (balance check, history) hơn writes.
- `JSONB` + `FOR UPDATE SKIP LOCKED` trên outbox table là implementation pattern chuẩn — không cần workaround.
- Flyway migration history được lưu trong DB — dễ audit và rollback nếu cần.
- Single database cho cả application state lẫn outbox — outbox write và ledger write trong cùng một ACID transaction không cần distributed coordination.

**Negative:**

- Hot account problem vẫn phải giải quyết ở application layer (ADR-0011) — database không tự handle.
- Cần `wal_level = logical` nếu Phase 2 muốn dùng Debezium CDC thay vì polling outbox.
- PostgreSQL schema migration với Flyway cần kỷ luật — không thể `ALTER TABLE` tùy tiện trên production table lớn.

**Neutral:**

- Schema version được track bởi Flyway, không phải application code.
- Không ảnh hưởng đến quyết định về tiền tệ ([ADR-0007](0007-money-representation.md)) hay concurrency strategy (ADR-0011) — cả hai đều cần giải quyết ở application layer dù dùng database nào.

## Risks & open questions

**Rủi ro 1: Connection pool exhaustion.** Với Java Virtual Threads ([ADR-0002](0002-language-framework.md)), nhiều VT chia sẻ connection pool — cần cấu hình HikariCP đúng. Default pool size của HikariCP (10 connections) có thể là bottleneck nếu nhiều concurrent requests chờ connection.

**Rủi ro 2: Schema lock khi migration.** `ALTER TABLE ADD COLUMN` trên bảng `ledger_entries` lớn có thể lock toàn bảng trong thời gian dài. Mitigation: dùng `ALTER TABLE ... ADD COLUMN ... DEFAULT NULL` (không lock trong PostgreSQL 11+), sau đó backfill. Phase 0-1 không phải vấn đề vì data nhỏ.

**Câu hỏi mở:**

- Indexing strategy cho `ledger_entries`: index trên `(account_id, created_at)` là rõ ràng; có cần partial index cho balance cache verification không?
- Phase 2: chuyển sang read replica cho reporting queries hay giữ single instance cho đơn giản?

## References

- [ADR-0001](0001-architectural-style.md) — quyết định RDBMS (không phải event store) là upstream context
- [ADR-0002](0002-language-framework.md) — Spring Data JPA + Hibernate dialect cho PostgreSQL
- [ADR-0007](0007-money-representation.md) — BIGINT minor units vs NUMERIC decision
- Source analysis: `Research/ledger-system-ADR/wiki/adr-003-database.md`
- Vault wikis referenced: `money-representation`, `rounding-in-money`, `optimistic-locking-for-ledgers`, `lost-update-on-balance`, `tigerbeetle`, `dual-write-problem`, `anti-pattern-mutable-balance`
