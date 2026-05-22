---

status: Accepted
date: 2026-05-20
----------------

# ADR-0001: Kiến trúc tổng thể — Modular monolith + double-entry ledger append-only trên PostgreSQL, KHÔNG full event sourcing

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-22 từ vault source `Research/ledger-system-ADR/wiki/adr-001-architectural-style.md`.

## Context

Ledger Service là backend quản lý ví điện tử nội bộ: tạo tài khoản, nạp tiền, chuyển khoản, xem số dư và lịch sử. Service phải đủ chính xác tài chính (audit trail, invariant kiểm tra được) và có ranh giới kiến trúc rõ để mở rộng được sau này.

Hai câu hỏi kiến trúc lớn phải giải quyết cùng lúc:

1. **Phong cách triển khai**: monolith, **modular monolith**, hay microservices?
2. **Cách lưu trữ dữ liệu tài chính**: event sourcing thuần túy, double-entry ledger append-only trên RDBMS, hay cột balance mutable đơn thuần?

Câu hỏi thứ hai đặc biệt quan trọng vì event sourcing là công nghệ hấp dẫn trong không gian ledger/fintech. Nhiều nguồn uy tín (Fowler, Kleppmann, Modern Treasury) đặt "log là nguồn sự thật" ở trung tâm của hệ thống tài chính. Nhưng đồng thời, cộng đồng cũng đã ghi lại rất rõ ràng những chi phí cấu trúc khổng lồ của full event sourcing nếu áp dụng không đúng context.

Quyết định này không phải "event sourcing hay không" theo nghĩa nhị phân. Đây là quyết định **bao nhiêu event sourcing**, **ở lớp nào**, và **với những đánh đổi nào**.

## Decision drivers

**1. Tính bất biến của dữ liệu tài chính là không thương lượng được.** Mọi giao dịch tiền tệ phải để lại dấu vết không thể xóa. Anti-pattern mutable-balance cho thấy rõ: khi chỉ lưu cột `balance` mutable, bất kỳ bug nào — retry nhầm, race condition, tính sai rate — đều thay đổi số dư mà không để lại bằng chứng forensic. Trong domain fintech, điều này không chấp nhận được.

**2. Kiểm tra tính nhất quán tự động (self-verification).** Double-entry bookkeeping mang lại invariant `Σ DEBIT == Σ CREDIT` cho mỗi transaction. Invariant này là một loại unit test chạy mãi mãi: nếu tiền "biến mất" do bug, tổng sẽ không cân. RDBMS enforce được constraint này trong cùng một transaction ACID.

**3. Chi phí vận hành phải phù hợp với scope hiện tại.** Phase 0-1 là single deployment unit. Mọi infrastructure overhead phải tương xứng với value thực sự mang lại — không over-engineer trước khi ship feature đầu tiên.

**4. Khả năng tách service ở Phase 2 phải được chuẩn bị sẵn từ bây giờ.** Thiết kế Phase 2 sẽ tách thành microservices. Ranh giới module phải cứng ngay từ đầu để việc tách sau này không đòi hỏi rewrite toàn bộ.

## Considered options

### Option A: Full event sourcing với dedicated event store

Toàn bộ domain (Account, Transaction, LedgerEntry, Idempotency) được event-sourced. Mọi thay đổi state được ghi là domain event bất biến vào event store (EventStoreDB, hoặc Postgres tự-manage). State của aggregate được rebuild bằng cách replay event stream. Balance là projection được materialize từ event log.

**Pros:**

- Audit trail hoàn hảo cho toàn bộ domain — mọi state change đều có event tương ứng.
- Khả năng replay để build projection mới từ lịch sử: thêm analytics view mới không cần backfill.
- Alignment với lý tưởng kiến trúc: "the log is the truth" và append-only computing được áp dụng triệt để.

**Cons:**

- **Event versioning là rào cản khổng lồ**: ngay khi schema một event cần đổi, toàn bộ event store phải được xử lý qua upcasting hoặc migration. Đây là technical debt rất khó trả về sau.
- **Projection rebuild cost tăng tuyến tính**: khi log phát triển, mỗi lần cần rebuild projection (bug fix, schema change, feature mới) sẽ tốn kém hơn theo thời gian. Cost này ẩn đi cho đến khi muộn.
- **No ad-hoc queries**: event log không phải data structure cho querying tùy ý. Mọi query cần projection chuẩn bị sẵn. Debug trực tiếp trên production bằng SQL trở nên không khả thi.
- **GDPR và fintech compliance phức tạp hơn nhiều**: khi dữ liệu cá nhân nằm trong event payload bất biến, xóa PII đòi hỏi crypto-shredding hoặc forgettable payloads — cả hai đều là infrastructure không nhỏ.
- **Dual-write problem nếu cần publish events ra ngoài**: giữ event store và message broker atomic đòi hỏi outbox pattern hoặc store-as-bus, đều có overhead vận hành.
- **Aggregate boundary sai là đắt vô cùng**: nếu chọn sai ranh giới aggregate (ví dụ 1 Account aggregate chứa cả Account metadata lẫn LedgerEntry history), sửa lại sau đòi hỏi stream migration phức tạp.
- 20+ câu hỏi pitfall checklist phải trả lời được trước khi đưa full ES vào production. Trả lời 20 câu hỏi đó là một sprint dài trước khi ship bất kỳ feature business nào.
- "Complete sprints will be lost" cho infrastructure work; CRUD entities như Account metadata, application configuration không cần ES.

### Option B: Modular monolith + double-entry append-only ledger entries trên PostgreSQL (LỰA CHỌN)

Hệ thống được tổ chức thành các module với ranh giới rõ ràng (package-by-feature). **Chỉ bảng `ledger_entries` là append-only và bất biến** — giống như journal kế toán. Các entity không-tài-chính (Account metadata, idempotency keys) vẫn là RDBMS thông thường. `balance` trên bảng `accounts` là cache được cập nhật trong **cùng một ACID transaction** với các entries tương ứng.

**Pros:**

- Lợi ích cốt lõi của event sourcing cho *dữ liệu ledger* được giữ lại: audit trail bất biến, balance có thể rebuild từ entries, temporal queries theo thời điểm.
- Tránh toàn bộ chi phí full ES: không cần event versioning, không cần projection rebuild infrastructure, queries tùy ý qua SQL vẫn hoạt động.
- PostgreSQL ACID đảm bảo: `INSERT INTO ledger_entries` và `UPDATE accounts SET balance = ...` trong cùng một transaction — nếu một cái fail, cả hai rollback. Không có dual-write problem.
- Invariant `Σ DEBIT == Σ CREDIT` có thể enforce bằng DB constraint hoặc service-layer check trong cùng transaction.
- Bảng `accounts` vẫn có thể `SELECT` tùy ý — debug dễ hơn.
- Outbox table (`outbox`) được lưu cùng PostgreSQL database — giải quyết dual-write theo cách đơn giản nhất (single DB transaction bao gồm cả outbox write).
- Module boundaries rõ ràng chuẩn bị cho Phase 2 service split mà không cần event stream migration phức tạp như trong full ES.

**Cons:**

- Không có khả năng replay để build projection mới từ lịch sử không-ledger (ví dụ: Account metadata history bị mất nếu cần thêm analytics view sau này).
- Balance cache có thể lệch khỏi entries nếu có bug trong sync logic — cần reconciliation job để detect ([ADR-0016](0016-reconciliation.md) — sẽ viết ở Phase 1).
- Không phải "full event sourcing" — nếu Phase 2+ cần ES thuần túy cho một module cụ thể, migration sẽ phức tạp hơn so với đã dùng ES từ đầu (nhưng đơn giản hơn rất nhiều so với migration từ mutable balance).

### Option C: Single-entry, cột balance mutable là nguồn sự thật duy nhất

Mỗi giao dịch chỉ chạy `UPDATE accounts SET balance = balance ± X WHERE id = :id`. Không có bảng transaction, không có bảng ledger_entries.

**Pros:**

- Cực kỳ đơn giản để implement ban đầu.
- Không cần hiểu double-entry bookkeeping.

**Cons:**

- Anti-pattern mutable-balance: mất toàn bộ forensic history, không thể audit, không thể reconstruct số dư sau bug.
- Không thể trả lời câu hỏi cơ bản: "Số dư $1,342 này đến từ đâu? Khi nào nó thay đổi lần cuối?"
- Mọi bug (retry nhầm, rate tính sai, race condition) đều làm thay đổi balance mà không để lại dấu vết.
- Race condition dẫn đến lost update on balance — không có cách detect ngoài reconciliation với external system.
- **Loại bỏ hoàn toàn** — không phải lựa chọn hợp lệ cho bất kỳ hệ thống tài chính nghiêm túc nào.

## Decision outcome

**Chọn Option B: Modular monolith + double-entry append-only ledger trên PostgreSQL.**

Quyết định này dựa trên một phân biệt quan trọng mà nhiều team bỏ qua: **"event-sourcing-like" cho dữ liệu ledger không đòi hỏi full event sourcing của toàn bộ domain.**

Bảng `ledger_entries` là **immutable log** — mỗi bút toán là một fact đã xảy ra, không bao giờ bị sửa hay xóa. Đây chính xác là tính chất mà "the log is the truth" mô tả và double-entry bookkeeping yêu cầu. `balance` là projection của log này — có thể rebuild bất kỳ lúc nào bằng `SELECT SUM(amount) FROM ledger_entries WHERE account_id = :id AND direction = 'CREDIT'` trừ đi debit.

Tuy nhiên, Account metadata (tên, trạng thái, created_at) không cần event history — đây là CRUD thông thường. Idempotency keys, outbox records — tất cả là CRUD. Full ES cho các entity này không mang lại giá trị mà chỉ thêm chi phí versioning và complexity.

Driver 1 (bất biến tài chính) dẫn đến Option B vì `ledger_entries` là append-only. Driver 2 (self-verification) được đáp ứng bởi double-entry invariant enforce trong transaction ACID. Driver 3 (chi phí vận hành) loại bỏ Option A vì full ES yêu cầu infrastructure phức tạp trước khi ship feature đầu tiên. Driver 4 (module boundaries) được đáp ứng bởi package-by-feature structure ([ADR-0004](0004-package-structure.md)). Option B là pattern phổ biến trong production ledger systems (Modern Treasury, Stripe, Formance) — full ES với dedicated event store là exception, không phải default.

## Consequences

**Positive:**

- Audit trail đầy đủ cho mọi giao dịch tài chính, tự nhiên qua `ledger_entries` append-only.
- Balance có thể rebuild từ entries bất kỳ lúc nào — reconciliation tự nhiên.
- Debug dễ dàng: `SELECT * FROM ledger_entries WHERE account_id = :id ORDER BY created_at` cho lịch sử đầy đủ.
- PostgreSQL ACID giải quyết dual-write atomically — không cần saga hay distributed transaction.
- Không có event versioning problem — schema của `ledger_entries` ổn định hơn nhiều so với domain event schema trong full ES.
- Module boundaries sẵn sàng cho Phase 2 service split mà không cần event stream migration.

**Negative:**

- Nếu Phase 2+ cần event sourcing thuần túy cho một module cụ thể, migration sẽ phức tạp hơn so với đã dùng ES từ đầu (nhưng đơn giản hơn so với migration từ mutable balance).
- `balance` cache có thể lệch — cần reconciliation job phát hiện ([ADR-0016](0016-reconciliation.md), sẽ viết Phase 1), không phải cơ chế tự-heal tự động.
- Không có khả năng build projection mới từ historical account-metadata events nếu sau này cần.

**Neutral:**

- Quyết định này không ảnh hưởng đến khả năng dùng outbox pattern cho event publishing ([ADR-0013](0013-event-publishing.md), Phase 1) — outbox vẫn hoạt động tốt với approach này.
- Optimistic locking ([ADR-0011](0011-concurrency-strategy.md), Phase 1) vẫn cần thiết để ngăn race condition trên `balance` cache.

## Risks & open questions

**Rủi ro 1: Balance cache drift.** Nếu có bug trong service layer khiến `ledger_entries` được ghi nhưng `accounts.balance` không được cập nhật (hoặc ngược lại), hai nguồn dữ liệu sẽ lệch nhau. Mitigation: reconciliation job (ADR-0016) chạy định kỳ, alert khi phát hiện drift. Không auto-fix — drift cần investigation.

**Câu hỏi mở:**

- Phase 2: khi tách service, module nào có thể cần event sourcing thực sự? (Dự kiến: Transfer saga orchestration nếu cần compensation.)
- Khi `ledger_entries` đạt hàng triệu rows, read performance có cần partition theo `account_id + created_at` không?

## References

- [ADR-0003](0003-database.md) — lý do chọn PostgreSQL
- [ADR-0004](0004-package-structure.md) — module boundaries chuẩn bị cho Phase 2
- [ADR-0005](0005-ledger-model.md) — double-entry append-only model detail
- [ADR-0006](0006-balance-representation.md) — balance là cache, ledger là truth
- Source analysis: `Research/ledger-system-ADR/wiki/adr-001-architectural-style.md`
- Vault wikis referenced: `is-event-sourcing-overkill`, `pitfall-checklist`, `event-versioning-problem`, `projection-rebuild-cost`, `no-ad-hoc-queries`, `gdpr-vs-immutable-log`, `event-store-dual-write`, `changing-aggregate-boundaries`, `double-entry-bookkeeping`, `the-log-is-the-truth`, `append-only-computing`, `anti-pattern-mutable-balance`, `balance-as-projection`, `ledger-as-event-log`
