---

status: Accepted
date: 2026-05-20
----------------

# ADR-0005: Ledger Model — Double-Entry, Append-Only

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-22 từ vault source `Research/ledger-system-ADR/wiki/adr-005-ledger-model.md`.

## Context

Ledger Service là backend quản lý ví điện tử nội bộ. Chức năng cốt lõi là ghi lại mọi sự dịch chuyển tiền tệ theo cách đảm bảo không có tiền nào bị "tạo ra" hay "biến mất" trong hệ thống, đồng thời giữ đầy đủ lịch sử để phục vụ audit và đối soát.

Kiến trúc tổng thể là modular monolith ([ADR-0001](0001-architectural-style.md)) với PostgreSQL làm storage duy nhất ([ADR-0003](0003-database.md)). Quyết định này ràng buộc model ledger phải vừa fit với relational database — không phải event store chuyên dụng — và vừa đảm bảo các tính chất tài chính cốt lõi.

Hệ thống cần phục vụ ba use case chính ở Phase 0-1: nạp tiền (top-up), chuyển khoản giữa các ví (transfer), và truy vấn số dư cùng lịch sử giao dịch. Các quyết định ở phase này đặt nền cho việc tách service ở Phase 2, do đó model cần tránh coupling ngầm khó tháo gỡ sau này.

## Decision drivers

- **Tính toàn vẹn tiền tệ**: tiền không được phép bị tạo ra hay phá hủy mà không có bút toán đối chiều. Phải có invariant kiểm tra được.
- **Audit trail đầy đủ**: mọi thay đổi số dư phải truy nguyên được tới nguyên nhân cụ thể — không phải chỉ "số dư là X" mà còn "tại sao số dư là X".
- **Khả năng đối soát (reconciliation)**: cần kiểm chứng tính nhất quán nội bộ bằng phép toán đơn giản, không phụ thuộc vào bên ngoài.
- **Đơn giản và fit với PostgreSQL**: tránh phức tạp thừa cho Phase 0-1; không cần event store hay CQRS phức tạp.
- **Không reversibility của lịch sử**: một khi bút toán được ghi, không được phép sửa hay xóa — sửa sai phải qua correcting entry.

## Considered options

### Option A — Double-entry, append-only (lựa chọn chính)

Mỗi nghiệp vụ tài chính tạo ra ít nhất hai bút toán trong bảng `ledger_entries`: một bên DEBIT và một bên CREDIT với tổng amount bằng nhau. Bảng này chỉ có INSERT, không có UPDATE hay DELETE.

**Pros:**

- Invariant `Σ DEBIT == Σ CREDIT` kiểm tra được bằng một câu SQL đơn (trial balance) — tính đúng đắn là toán học, không phải niềm tin vào logic application.
- Lịch sử hoàn chỉnh và bất biến: mọi bút toán đều có timestamp, tham chiếu tới transaction, và có thể replay để kiểm chứng số dư tại bất kỳ thời điểm.
- Align tốt với "the log is the truth" và append-only computing — kết hợp tự nhiên với cached balance ở [ADR-0006](0006-balance-representation.md).
- Khi có lỗi, sửa bằng correcting entry — reversing entry + correct entry — không mất trail.
- Fit hoàn toàn với PostgreSQL relational model; không cần infrastructure đặc biệt.

**Cons:**

- Developer cần hiểu debit/credit semantics, bao gồm normal balance per account type — learning curve ban đầu.
- Mỗi nghiệp vụ tạo ít nhất 2 rows thay vì 1 (storage tăng 2x, nhưng không đáng kể cho Phase 0-1).
- Phải xử lý account `SYSTEM_FUNDING` đặc biệt cho top-up ([ADR-0009](0009-system-funding-account.md) — sẽ viết ở Phase 1).

### Option B — Single-entry, mutable balance

Chỉ lưu `balance` trên bảng `accounts`. Mỗi lần có tiền vào/ra, cập nhật cột balance bằng `UPDATE accounts SET balance = balance ± X`.

**Pros:**

- Code đơn giản hơn nhiều cho prototype.
- Không cần hiểu double-entry.

**Cons:**

- Không có lịch sử: không thể trả lời "số dư này đến từ đâu?" Đây là anti-pattern mutable-balance đã được industry documented rõ.
- Không có invariant tự kiểm tra — bug tính toán có thể làm tiền biến mất mà không có alert.
- Không thể audit, không thể reconcile với nguồn bên ngoài.
- Khi xảy ra sự cố, không có forensic data để investigate.

### Option C — Hybrid: ledger entries nhưng balance vẫn là primary truth

Ghi `ledger_entries` nhưng balance trên `accounts` là thứ code đọc và quyết định, thay vì derive từ entries.

**Pros:**

- Có lịch sử hơn Option B.

**Cons:**

- Double-entry invariant không còn ý nghĩa nếu balance không được derive từ entries.
- Vẫn có rủi ro drift giữa balance và sum(entries) nếu không có cơ chế enforce.
- Phức tạp giữa hai nguồn truth mà không có lợi ích rõ ràng — worst of both worlds.

## Decision outcome

Chọn **Option A — Double-entry, append-only**. Bảng `ledger_entries` là nguồn sự thật duy nhất; mỗi nghiệp vụ tạo ra ít nhất một cặp bút toán DEBIT + CREDIT với `Σ DEBIT == Σ CREDIT`. Bảng này là insert-only — không có UPDATE, không có DELETE. Mọi sửa sai đều qua correcting entry mới.

Lý do cốt lõi: ledger service không phải là prototype — đây là nền tảng tài chính cần audit trail và tính toàn vẹn. Option B loại bỏ ngay vì là anti-pattern đã documented. Option C tạo hai nguồn truth mà không giải quyết vấn đề gốc. Option A là chuẩn ngành (Modern Treasury, Stripe, Formance đều dùng model này) và fit tốt với PostgreSQL mà không cần infrastructure phức tạp.

## Consequences

**Positive:**

- Trial balance (`Σ debit == Σ credit`) là kiểm tra tính đúng đắn chạy được bất cứ lúc nào.
- Lịch sử đầy đủ cho reconciliation job ([ADR-0016](0016-reconciliation.md), Phase 1).
- Foundation cho point-in-time balance query — số dư tại ngày T có thể tính lại từ entries.

**Negative:**

- Mỗi nghiệp vụ phải insert ≥2 rows; code phải handle building bút toán đúng.
- Team cần nắm debit/credit convention và normal balance per account type.

**Neutral:**

- Top-up cần account `SYSTEM_FUNDING` làm counterpart — được quyết định ở ADR-0009 (Phase 1).
- Sửa lỗi qua correcting entry thay vì UPDATE là thay đổi tư duy lớn nhất cho developer quen CRUD.

## Risks & open questions

- **Convention debit/credit**: cần document rõ trong code convention: account loại wallet là credit-normal (credit tăng balance, debit giảm). `SYSTEM_FUNDING` là debit-normal. Nếu nhầm chiều, trial balance vẫn cân nhưng số dư sẽ sai.
- **Multi-leg transaction**: nếu tương lai cần split fee hay VAT, cần đảm bảo Σ DEBIT == Σ CREDIT trên toàn bộ transaction, không phải trên từng cặp entry riêng lẻ.
- **Correcting entry workflow**: cần clear runbook khi phát hiện bút toán sai sau khi đã committed — ai được phép tạo correcting entry, process approval là gì.

## References

- [ADR-0001](0001-architectural-style.md) — modular monolith + double-entry chiến lược tổng thể
- [ADR-0006](0006-balance-representation.md) — balance là cache, ledger entries là truth
- [ADR-0007](0007-money-representation.md) — BIGINT integer minor units cho amount
- Source analysis: `Research/ledger-system-ADR/wiki/adr-005-ledger-model.md`
- Vault wikis referenced: `double-entry-bookkeeping`, `debit-credit`, `accounting-entry`, `accounting-transaction`, `single-entry-vs-double-entry`, `anti-pattern-mutable-balance`, `the-log-is-the-truth`, `append-only-computing`, `correcting-entry`
