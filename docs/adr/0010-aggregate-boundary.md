---

status: Accepted
date: 2026-05-20
----------------

# ADR-0010: Aggregate Boundary — Account là Đơn Vị Locking

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-22 từ vault source `Research/ledger-system-ADR/wiki/adr-010-aggregate-boundary.md`.

## Context

Hệ thống dùng optimistic locking để xử lý concurrent write vào cùng một account ([ADR-0011](0011-concurrency-strategy.md), Phase 1). Trước khi chốt locking strategy phải xác định **locking boundary**: khi hai request đụng vào "vùng dữ liệu" nào thì mới conflict.

Trong DDD đây là câu hỏi **aggregate boundary**: aggregate là đơn vị transactional consistency — mọi thay đổi bên trong một aggregate xảy ra trong một transaction, và conflict chỉ xảy ra khi hai transaction cùng write vào cùng một aggregate. Câu hỏi: một aggregate gồm những gì — một `Account` đơn lẻ, tất cả account của một customer, hay rộng hơn?

## Decision drivers

- **Contention thấp nhất**: lock đủ nhỏ để hai request không liên quan không block nhau (transfer A→B không nên chặn transfer C→D).
- **Bảo vệ đúng invariant**: `balance >= 0` sau debit là invariant **per-account**; `Σ DEBIT == Σ CREDIT` là invariant per-posting (thuộc Transaction, không phải per-aggregate-account).
- **Forward compatibility**: ranh giới phải tách được thành service riêng ở Phase 2 mà không phải restructure lớn.

## Considered options

### Option A — Account-per-aggregate (chọn)

Mỗi `Account` là một aggregate độc lập; lock unit là account row qua `@Version` trên bảng `accounts`. Transfer lock CẢ HAI account (sender + receiver) theo thứ tự `id` tăng dần để tránh deadlock.

**Pros:**
- Contention chỉ xảy ra khi cùng một account bị write đồng thời — hợp lý nhất cho e-wallet.
- Hai transfer không liên quan chạy song song hoàn toàn.
- Invariant `balance >= 0` là per-account, fit tự nhiên với account-level lock.
- Khớp `@Version` của JPA — pattern Spring chuẩn, không cần custom lock infrastructure.
- Ranh giới rõ: Account = đơn vị tách service ở Phase 2.

**Cons:**
- Transfer phải lock 2 account → cần lock-ordering protocol (lock `id` nhỏ trước) để tránh deadlock.
- `SYSTEM_FUNDING` là hot account ([ADR-0009](0009-system-funding-account.md)) — nhiều top-up đồng thời cùng lock nó; chấp nhận được ở Phase 0-1 traffic thấp.

### Option B — Customer-per-aggregate

Một `Customer` là aggregate gồm tất cả account của họ; lock toàn cluster account khi bất kỳ account nào bị update.

**Cons:** contention quá cao (đụng 1 trong N account block cả N); transfer giữa hai customer vẫn cần lock 2 aggregate; ranh giới không tự nhiên; khó tách service (phải split aggregate). Loại.

### Option C — Coarser (currency-per-aggregate / global)

Mọi account cùng currency (hoặc toàn hệ thống) trong một aggregate.

**Cons:** contention extreme, throughput bị giới hạn bởi một lock duy nhất, không scale. Loại ngay.

## Decision outcome

Chọn **Option A — Account-per-aggregate**. Mỗi `Account` là aggregate độc lập, bảo vệ bởi `@Version` (optimistic lock) trên cột `version`. Transfer lock cả hai account theo `id` tăng dần.

Lý do: granularity tự nhiên nhất cho e-wallet; invariant quan trọng nhất (`balance >= 0`) là per-account; contention thấp nhất; ranh giới rõ cho việc tách service ở Phase 2.

## Consequences

**Positive:**
- Hai transfer không liên quan chạy song song — throughput tốt ở trường hợp thường.
- `@Version` là standard JPA, không cần custom lock logic.
- Account-level granularity tự nhiên cho Phase 2 (Account service = aggregate service).

**Negative:**
- Transfer acquire 2 lock — convention lock-by-id-ascending phải document + enforce trong code review + có unit test kiểm chứng.
- `SYSTEM_FUNDING` hot account → conflict rate cao khi load tăng; Phase 1+ cần xem [[hot-account-problem]] (async queue hoặc sub-account sharding).

**Neutral:**
- Validation xuyên account (vd "customer không quá N account") làm ở application layer, không ở aggregate level — pattern set-based validation chuẩn trong DDD.
- `SYSTEM_FUNDING` cùng bảng `accounts` nhưng `owner_ref` null (không thuộc customer nào).

## Risks & open questions

- **Deadlock**: T1 (A→B) và T2 (B→A) đồng thời có thể deadlock nếu thiếu lock ordering. Convention lock-by-id-ascending phải được enforce + test.
- **Hot account tương lai**: `SYSTEM_FUNDING` sẽ là bottleneck khi traffic tăng → async queue hoặc sub-account sharding (ADR tương lai).
- **Cross-aggregate invariant**: invariant xuyên account của cùng customer (nếu cần) phải xử lý ở application layer với eventual consistency.

## References

- [ADR-0005](0005-ledger-model.md) — double-entry append-only; ledger model tổng quan
- [ADR-0006](0006-balance-representation.md) — balance là cache update trong cùng tx; Account aggregate owns balance
- ADR-0009 — system funding account (Phase 1, chưa distill)
- ADR-0011 — concurrency: optimistic + retry (Phase 1, chưa distill)
- Source analysis: `Research/ledger-system-ADR/wiki/adr-010-aggregate-boundary.md`
- DDD tactical mapping (Account/Transaction/LedgerEntry, cross-aggregate object placement): vault `60 - Software Architect/DDD/cross-aggregate-objects-and-the-ledger-entry-question.md`
- Vault wikis referenced: `optimistic-locking-for-ledgers`, `lost-update-on-balance`, `hot-account-problem`, `set-based-validation`
