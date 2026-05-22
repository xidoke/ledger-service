---

status: Accepted
date: 2026-05-20
----------------

# ADR-0006: Balance Representation — Cache trong cùng transaction DB

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-22 từ vault source `Research/ledger-system-ADR/wiki/adr-006-balance-representation.md`.

## Context

Theo [ADR-0005](0005-ledger-model.md), `ledger_entries` là nguồn sự thật duy nhất — số dư "thật" của một account là `Σ amount` của tất cả entries thuộc account đó. Câu hỏi vận hành đặt ra ngay lập tức: mỗi khi cần đọc số dư, có nên tính lại từ toàn bộ lịch sử entries không?

API `GET /accounts/{id}` cần trả về số dư hiện tại. Endpoint top-up và transfer cũng cần đọc số dư để kiểm tra khả năng thực hiện giao dịch (ví dụ: transfer không vượt quá balance hiện có). Nếu mỗi lần đọc phải chạy `SELECT SUM(amount) FROM ledger_entries WHERE account_id = ?`, hiệu năng sẽ bị ảnh hưởng nghiêm trọng khi số lượng entries tăng lên theo thời gian.

Đồng thời, với aggregate boundary (Account là aggregate root) và optimistic locking ([ADR-0011](0011-concurrency-strategy.md), Phase 1) dùng cột `version` trên `accounts`, cột `balance` trên bảng `accounts` còn có vai trò là checkpoint để xác định "trạng thái hiện tại" của aggregate tại version hiện tại.

## Decision drivers

- **Hiệu năng đọc**: balance check xảy ra trên mỗi request transfer/top-up — phải là O(1), không phải O(n) theo số entries.
- **Tính chính xác**: số dư đọc được phải luôn phản ánh đúng state sau giao dịch gần nhất, không được stale.
- **Nguồn sự thật rõ ràng**: `ledger_entries` là truth, `accounts.balance` nếu có thì chỉ là derived — phải enforce không để hai thứ diverge.
- **Khả năng phục hồi**: nếu cache corrupted, phải rebuild được từ entries mà không mất data.
- **Đơn giản hóa vận hành**: Phase 0-1 không cần caching infrastructure phức tạp (Redis, materialized view separate process).

## Considered options

### Option A — Cache balance trong cùng DB transaction (lựa chọn chính)

Cột `balance` trên bảng `accounts` được cập nhật **trong cùng database transaction** với thao tác INSERT vào `ledger_entries`. Hai thao tác atomic: hoặc cả hai thành công, hoặc cả hai rollback.

```sql
BEGIN;
INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, ...)
  VALUES (..., 'ACC-001', 'CREDIT', 10000, ...),
         (..., 'SYSTEM_FUNDING', 'DEBIT', 10000, ...);

UPDATE accounts SET balance = balance + 10000, version = version + 1
  WHERE id = 'ACC-001' AND version = :expected_version;

UPDATE accounts SET balance = balance - 10000, version = version + 1
  WHERE id = 'SYSTEM_FUNDING' AND version = :expected_version;
COMMIT;
```

Reconciliation job ([ADR-0016](0016-reconciliation.md), Phase 1) chạy định kỳ (ví dụ: mỗi giờ hoặc mỗi đêm) kiểm tra `accounts.balance == SUM(ledger_entries.amount * sign(direction))` cho từng account. Nếu có drift, alert và rebuild cache.

**Pros:**

- Balance read là O(1): `SELECT balance FROM accounts WHERE id = ?`.
- Strong consistency: không có khoảnh khắc nào balance "sai" từ góc nhìn của database — ACID đảm bảo.
- Kết hợp tự nhiên với optimistic locking (cột `version` cùng bảng `accounts`).
- Nếu có bug trong update balance, reconciliation job sẽ phát hiện — cache có thể rebuild từ entries.
- Không cần infrastructure phụ (Redis, separate process).

**Cons:**

- Mỗi write transaction phải update cả hai bảng — chi phí write tăng nhẹ.
- Nếu có nhiều account cùng bị update trong một batch, phải cẩn thận lock ordering để tránh deadlock.
- Cache có thể bị "dirty" nếu developer bypass application layer và update entries trực tiếp bằng SQL — phải có convention cấm điều này.

### Option B — Derived-on-read only (không cache)

Không lưu `balance` ở đâu cả. Mỗi lần cần số dư, chạy `SELECT SUM(...) FROM ledger_entries WHERE account_id = ?`.

**Pros:**

- Không bao giờ có drift giữa cache và truth — không có cache.
- Code đơn giản hơn: không cần update balance khi write entries.

**Cons:**

- Hiệu năng không chấp nhận được khi account có nhiều entries. Account hoạt động 1 năm có thể có hàng chục ngàn entries — mỗi balance check là full table scan nếu không index tốt.
- Vẫn cần index và snapshot để tối ưu; khi đó phức tạp không khác gì Option A nhưng không transactionally safe.
- Mỗi transfer phải đọc balance để check khả dụng — nếu không cache, đọc balance cùng lúc với lock account sẽ tạo thêm latency.
- Không kết hợp được với optimistic locking trên cột `version` (vì `version` cũng nằm trên `accounts` row cùng `balance`).

### Option C — Mutable balance only (không có ledger entries — anti-pattern)

Xóa bỏ `ledger_entries`, chỉ dùng `accounts.balance`. Đây thực chất là quay lại anti-pattern mutable-balance.

**Pros:**

- Tốc độ write nhanh nhất.
- Code đơn giản nhất.

**Cons:**

- Mâu thuẫn hoàn toàn với [ADR-0005](0005-ledger-model.md) đã được accepted.
- Không có lịch sử, không có audit trail, không có khả năng reconcile.
- Đây là anti-pattern đã được documented rõ.

Option này không khả thi và bị loại ngay.

## Decision outcome

Chọn **Option A — cache balance trong cùng DB transaction**. Cột `balance` trên bảng `accounts` là materialized cache của `Σ ledger_entries`, được cập nhật atomically cùng với mỗi insert vào `ledger_entries`. `ledger_entries` là nguồn sự thật; `accounts.balance` là derived read cache.

Lý do: với PostgreSQL và ACID transaction, đây là cách đơn giản nhất để có cả hiệu năng đọc O(1) lẫn strong consistency. Option B loại vì hiệu năng không chấp nhận được ở scale. Option A còn kết hợp hoàn toàn tự nhiên với optimistic locking (`version` column), không cần thêm infrastructure.

Reconciliation job ([ADR-0016](0016-reconciliation.md), Phase 1) là cơ chế phát hiện drift và rebuild cache nếu cần — đảm bảo tính tự phục hồi của hệ thống.

## Consequences

**Positive:**

- Balance read là point lookup, không phải aggregate scan — latency thấp và predictable.
- Strong consistency với ACID: không thể có entries mà balance chưa phản ánh, hoặc ngược lại.
- Reconciliation job vừa là safety net vừa là kiểm tra tính đúng đắn liên tục của hệ thống.

**Negative:**

- Cần discipline: mọi thay đổi `ledger_entries` phải đi qua application layer — không được phép update trực tiếp bằng SQL mà không đồng thời update `accounts.balance`.
- Phải xử lý lock ordering cẩn thận khi transfer giữa hai account (luôn lock account có ID nhỏ hơn trước để tránh deadlock).

**Neutral:**

- Reconciliation job là operational requirement — phải được schedule và monitored từ ngày đầu, dù scope nhỏ ở Phase 0-1.
- Nếu sau này muốn tách read/write (CQRS), `accounts.balance` đã là projection sẵn — migration dễ hơn so với Option B.

## Risks & open questions

- **Drift detection frequency**: reconciliation job nên chạy bao lâu một lần? Mỗi giờ là hợp lý cho Phase 0-1; khi scale lên cần continuous background check.
- **Rebuild strategy**: nếu balance bị sai, procedure rebuild là gì? Cần script `UPDATE accounts SET balance = (SELECT SUM...) FROM ledger_entries WHERE ...` có thể chạy online (không downtime) — cần test trước.
- **Snapshot cho long-lived accounts**: với account có lịch sử dài (nhiều năm), reconciliation job tính lại từ đầu sẽ chậm. Giải pháp là snapshotting — lưu balance tại checkpoint và chỉ replay entries từ đó. Có thể cần ở Phase 2.

## References

- [ADR-0005](0005-ledger-model.md) — `ledger_entries` là nguồn sự thật; balance là cache derive từ đó
- Source analysis: `Research/ledger-system-ADR/wiki/adr-006-balance-representation.md`
- Vault wikis referenced: `balance-as-projection`, `anti-pattern-mutable-balance`, `hot-account-problem`, `reconciliation`, `snapshotting`
