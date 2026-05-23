---

status: Accepted
date: 2026-05-20
----------------

# ADR-0014: PII Handling — Forgettable Payload

## Status

Accepted (2026-05-20). Distilled tới repo 2026-05-24 (LDG-58) từ vault source `Research/ledger-system-ADR/wiki/adr-014-pii-handling.md`. Phản ánh trạng thái hiện tại (`accounts.owner_ref`) — xem §Trạng thái triển khai.

## Context

Ledger là append-only (ADR-0005): `ledger_entries` + `transactions` bất biến, không UPDATE/DELETE. Đây là nền của audit trail. Nhưng hệ thống cần lưu thông tin chủ tài khoản, mà PII (tên/email/phone) thì **GDPR Art.17 + Nghị định 13/2023/NĐ-CP** yêu cầu **xoá được** khi user yêu cầu. Hai ràng buộc xung đột cấu trúc: **immutable ledger ≠ deletable PII**.

Lối thoát (xem [[gdpr-vs-immutable-log]]): tách *fact* khỏi *identity*. "Account X nhận 100k ngày Y" là financial record phải giữ (regulator giữ 5–7 năm); *ai* sở hữu account X là personal data có thể bị erase. Nếu PII bị nhúng vào `ledger_entries` / outbox payload (vd `description` chứa tên), không thể xoá mà không phá immutability → rủi ro compliance nghiêm trọng. Phải design-for-erasure từ đầu (retrofit khi đã có triệu record là cực đắt).

## Decision drivers

- **Right-to-erasure là bắt buộc** (GDPR Art.17 / NĐ 13/2023) — không optional.
- **Immutability của ledger là non-negotiable** (ADR-0005) — không hi sinh để chiều GDPR.
- **Legal certainty** — cần giải pháp defend được, không phải "có thể chấp nhận".
- **Đúng quy mô** — team nhỏ, không over-engineer.

## Considered options

### Option A — Forgettable Payload: tách PII ra bảng riêng (CHỌN)

PII sống trong một bảng mutable riêng (CRUD + hard-DELETE được); các bảng append-only + outbox **chỉ** chứa id opaque (UUID), không bao giờ chứa PII. Erasure request → hard-DELETE row PII; financial facts (số tiền/thời gian/account) giữ nguyên, identity-linkage không còn resolve được.

**Pros:** GDPR compliance rõ ràng (physical deletion, không grey area); không encryption overhead; đơn giản maintain; tách bạch immutable-facts ↔ mutable-identity.
**Cons:** cần JOIN khi hiển thị tên; cần code-review discipline (cấm nhúng PII vào free-text append-only); xoá xong UI phải handle null gracefully.

### Option B — Crypto-shredding

Mã hoá PII trong payload bằng per-subject key; erase = destroy key. **Cons:** legal status còn tranh cãi (encrypted bytes có còn là personal data? GDPR Recital 26 chưa có ruling dứt khoát) — không chấp nhận grey area cho hệ tài chính; + key-management/encryption overhead không tương xứng quy mô. **Hoãn/loại.**

### Option C — Để PII trong events (anti-pattern)

Nhúng tên/email vào `ledger_entries`/outbox cho tiện query. **Loại:** vi phạm Art.17 hoàn toàn, không có đường xoá khỏi immutable log.

## Decision outcome

**Chọn Option A — Forgettable Payload.** Compliance rõ ràng + đơn giản nhất cho quy mô này. Crypto-shredding loại vì legal grey area + overhead.

**Nguyên tắc vận hành bắt buộc:**
1. PII **chỉ** nằm trong bảng mutable (xoá được) — không bao giờ trong `ledger_entries`, `transactions`, hay outbox payload.
2. `description` + mọi free-text trong append-only = **financial metadata** (vd "Top-up via VNPay"), không bao giờ chứa PII.
3. Code-review checklist: kiểm tra PII leakage vào append-only tables.
4. Erasure request → hard-DELETE PII; giữ nguyên toàn bộ ledger entries liên quan.

## Trạng thái triển khai (2026-05-24)

- **Hiện tại chưa có PII giàu** (chưa auth/customer thật). Slot duy nhất là `accounts.owner_ref` — VARCHAR **nullable**, client cung cấp, là **opaque external reference** (client tự đặt; có thể là id hệ họ, không nên là email/tên trần). Nó nằm trên `accounts` (bảng **mutable**) nên erase được bằng `UPDATE … SET owner_ref = NULL` / xoá account, **không** đụng append-only.
- **Đã xác nhận compliance**: `ledger_entries`, `transactions`, và outbox payload (`TransferPosted`/`TopupPosted`) chỉ carry **account_id (UUID)** + amount/currency/timestamp — **không** owner_ref, không PII. `owner_ref` chỉ xuất hiện ở aggregate `account` (entity/mapper/response).
- **Defer**: bảng `customers` riêng cho PII giàu (name/email/phone + soft/hard-delete) dựng khi auth/customer thật xuất hiện (Phase 3+). Lúc đó append-only chuyển sang reference `customer_id`; `owner_ref` là tiền thân của pattern này.

## Consequences

**Positive:** GDPR erasure khả thi + defend được; PII tập trung một chỗ; financial facts preserve theo regulator; đơn giản hơn crypto-shredding.
**Negative:** cần JOIN khi hiển thị tên (tương lai); không có cơ chế tự phát hiện PII leakage vào free-text → dựa code-review; UI phải handle null sau erasure.
**Neutral:** bảng PII là CRUD thường; "forgettable payload" là pattern community phổ biến ([[forgettable-payloads]]).

## Risks & open questions

- **PII leakage qua free-text** (rủi ro lớn nhất): cân nhắc lint rule / type-safe wrapper / test assertion cấm PII vào append-only.
- **Regulatory retention vs erasure**: NHNN giữ records X năm — erasure có thể phải delay tới hết retention; rõ trong ToS + process.
- **Multi-service (Nấc 2+)**: mỗi service tự giữ PII hay tập trung qua Customer Service — ADR tương lai.

## References

- [ADR-0005](0005-ledger-model.md) — append-only ledger: lý do immutability là non-negotiable
- [ADR-0013](0013-event-publishing.md) — outbox payload chỉ carry id, không PII (nguyên tắc 1/2 áp dụng tại đây)
- Source analysis: `Research/ledger-system-ADR/wiki/adr-014-pii-handling.md`
- Concepts: `Research/event-sourcing-pitfalls/wiki/{gdpr-vs-immutable-log, forgettable-payloads, crypto-shredding}.md`
