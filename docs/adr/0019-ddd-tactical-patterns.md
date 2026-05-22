---

status: Accepted
date: 2026-05-20
----------------

# ADR-0019: DDD Tactical Patterns bên trong mỗi module

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-23 từ vault source `Research/ledger-system-ADR/wiki/adr-019-ddd-tactical-patterns.md`. Bao gồm tinh chỉnh LedgerEntry từ LDG-40 (xem §Cập nhật triển khai).

## Context

Modular monolith + package-by-feature ([ADR-0004](0004-package-structure.md)). Câu hỏi: bên trong `domain` của mỗi module, code tổ chức theo triết lý nào? Tài chính có invariant cứng (`Σ DEBIT == Σ CREDIT`; `balance >= 0`) phải được enforce ở MỘT nơi rõ ràng, không phân tán.

Thuật ngữ: **Aggregate** (nhóm entity/VO treat như một đơn vị transactional), **Aggregate root** (entity duy nhất bên ngoài tham chiếu trực tiếp; mọi thay đổi đi qua nó), **Repository** (truy cập storage cho mỗi aggregate root), **Value Object** (định danh bởi giá trị, immutable), **Domain Event** (ghi nhận điều đã xảy ra).

## Decision drivers

- Enforce invariant **tại domain**, không phải service layer (tránh bị bypass khi thêm code path mới).
- `Σ DEBIT == Σ CREDIT` phải có enforcement point rõ ràng (`Transaction.post()`).
- Ubiquitous language: code đọc như nghiệp vụ (`account.debit(...)`, `Money.of(...)`).
- Chuẩn bị tách service Phase 2 (domain portable, không phụ thuộc Spring).

## Considered options

### Option A — DDD tactical đầy đủ (chọn)

Aggregate + Repository-per-root + Value Object + Domain Event.
- **Account** aggregate root: `debit/credit` enforce ACTIVE + `balance >= 0`.
- **Transaction** aggregate root: `addEntry()` + `post()` enforce zero-sum.
- **Value Objects**: `Money` (long minor units — [ADR-0007](0007-money-representation.md)), typed `AccountId`/`TransactionId` (wrap UUID — [ADR-0031](0031-identifier-strategy.md)), `Direction` enum.
- **Domain Event** (`TransactionPostedEvent`) qua Spring `ApplicationEventPublisher` in-process; cross-module async qua outbox.

**Pros:** invariant không bị bypass; test domain không cần Spring; typed VO bắt lỗi nhầm ID/sign tại compile time; tách coupling cross-module qua event; portable cho Phase 2.
**Cons:** nhiều class hơn (~30-40% boilerplate VO); mapping domain ↔ JPA entity phức tạp hơn; learning curve DDD.

### Option B — Anemic CRUD (anti-pattern)

Domain là data bag (getter/setter), logic ở service.
**Cons:** invariant phân tán dễ bỏ sót; không ubiquitous language; Fowler gọi là *Anemic Domain Model anti-pattern*. Loại.

### Option C — Pure functional records, không aggregate behavior

**Cons:** Java 21 không functional-first (phải tự implement `Result<A,E>`); không có aggregate làm locking unit / enforcement point; onboarding khó. Loại.

## Decision outcome

Chọn **Option A**. Driver quyết định: tiền thật → `balance >= 0` và `Σ DEBIT == CREDIT` là correctness requirement, aggregate root là nơi duy nhất đúng để enforce. Typed `Money`/ID bắt lỗi tại compile time. Domain Event là cơ chế cross-module sạch nhất.

## Consequences

**Positive:** invariant enforce tại aggregate; test domain milli-giây không cần Spring; `Money` validated ngăn silent error; readability cao.
**Negative:** nhiều class boilerplate; mapping JPA cẩn thận; cần hiểu DDD trước khi đóng góp (ADR này là một phần onboarding).
**Neutral:** `LedgerEntry` không có Repository riêng — query lịch sử qua read-only `LedgerEntryQueryRepository` (CQRS nhỏ).

## Cập nhật triển khai (2026-05-22, LDG-40)

Khi code LDG-40, một chi tiết được **tinh chỉnh** (không đảo quyết định lõi):

- **`LedgerEntry` = immutable shared fact ở `common/domain`**, KHÔNG phải child *riêng* của `Transaction` aggregate. Lý do: trong accounting model `LedgerEntry` thuộc về **cả** Account *và* Transaction; mô hình hóa như một fact dùng chung (đúng tinh thần log-is-truth [ADR-0005](0005-ledger-model.md)) giải quyết tension đó + tránh cross-feature dependency `account → ledger` dưới ArchUnit ([ADR-0004](0004-package-structure.md)). `Account.debit()/credit()` **vẫn emit** `LedgerEntry`; `Transaction` giữ + validate danh sách entry.
- `Money` dùng `long` minor units ([ADR-0007](0007-money-representation.md)), KHÔNG BigDecimal.

Phân tích đầy đủ (Option A vs B cho vị trí LedgerEntry): vault `60 - Software Architect/DDD/cross-aggregate-objects-and-the-ledger-entry-question.md`.

## Risks & open questions

- `Transaction` aggregate phình to nếu thêm nhiều logic (compensation/reversal/fee) — invest ranh giới tốt từ đầu.
- Reversal Phase 1: thiên về `Transaction` với `type=REVERSAL` + `reversedTransactionId`, không inheritance.

## References

- [ADR-0004](0004-package-structure.md) — package-by-feature; ArchUnit
- [ADR-0005](0005-ledger-model.md) — ledger model, log-is-truth
- [ADR-0010](0010-aggregate-boundary.md) — Account = locking unit
- [ADR-0031](0031-identifier-strategy.md) — typed UUID id
- Source analysis: `Research/ledger-system-ADR/wiki/adr-019-ddd-tactical-patterns.md`
