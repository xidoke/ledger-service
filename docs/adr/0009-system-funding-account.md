---

status: Accepted
date: 2026-05-20
----------------

# ADR-0009: System Funding Account — Nguồn Double-Entry cho Top-Up

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-23 từ vault source `Research/ledger-system-ADR/wiki/adr-009-system-funding-account.md`.

## Context

[ADR-0005](0005-ledger-model.md) yêu cầu mọi transaction `Σ DEBIT == Σ CREDIT`. Transfer giữa hai user account cân bằng tự nhiên (DEBIT A, CREDIT B). Nhưng **top-up** (nạp tiền từ ngoài vào ví) chỉ có một chiều rõ ràng — CREDIT user account; không có account nào "cho" tiền. Nếu chỉ ghi một leg, invariant vỡ. Tiền thật đến từ ngoài hệ thống (ngân hàng, payment processor); model kế toán phải phản ánh đúng nguồn, nếu sai ở Phase 0 thì Phase 2 (tích hợp payment thật) phải rewrite.

## Decision drivers

- **Bảo toàn double-entry** — không exception cho top-up (giữ khả năng self-check qua trial balance).
- **Truy nguyên nguồn tiền** — mọi đồng có counterpart.
- **Đơn giản cho Phase 0-1** nhưng model đúng để Phase 2 extend tự nhiên.
- **Không phá reconciliation** — account đặc biệt phải có balance truy vấn được.

## Considered options

### Option A — Account đặc biệt `SYSTEM_FUNDING` (chọn)

Một account đặc biệt đại diện nguồn vốn ngoài. Top-up tạo cặp: `DEBIT SYSTEM_FUNDING / CREDIT user_account`. `SYSTEM_FUNDING` là **debit-normal**; balance âm là *bình thường + có ý nghĩa* — nó đo tổng nghĩa vụ của platform với users: `balance(SYSTEM_FUNDING) + Σ balance(user_accounts) == 0` (khi chưa có withdrawal).

Trong schema hiện tại ([ADR-0010](0010-aggregate-boundary.md) + migration accounts): `SYSTEM_FUNDING` là một row trong `accounts` với **id well-known** và **`owner_ref = NULL`**, seed bằng migration (V5).

> **Amendment (2026-05-23, LDG-44):** thêm cột **`account_type`** (`USER`/`SYSTEM`) + enum `AccountType` trên `Account` aggregate. SYSTEM_FUNDING có `account_type = SYSTEM` và `Account.debit()` cho phép số dư âm khi type là SYSTEM (USER thì cấm overdraw). Điều này **thay** ý ban đầu "nhận diện chỉ bằng id + null owner, không thêm cột type": balance policy là hành vi domain nên gắn vào type tường minh (lý do đầy đủ: vault `ledger-systems/wiki/account-types-and-negative-balance`).

**Pros:** invariant giữ nguyên, trial balance pass; `|balance(SYSTEM_FUNDING)|` = tổng outstanding user balance → check reconciliation tự nhiên; Phase 2 chỉ cần thêm external payment reference, không đổi model; chuẩn ngành (Stripe/Modern Treasury có "platform settlement account").
**Cons:** phải document rõ "balance âm là bình thường" kẻo bị tưởng bug.

### Option B — Special-case top-up, bypass double-entry

Chỉ ghi một leg CREDIT + cờ `is_external_funding`.

**Cons:** vỡ `Σ DEBIT == Σ CREDIT` → mất self-check; tạo tiền lệ exception nguy hiểm; không truy nguyên nguồn; không scale Phase 2. Loại.

### Option C — Nhiều funding account theo kênh (`FUNDING_BANK_TRANSFER`, …)

**Cons:** over-engineer Phase 0-1 (chưa có payment gateway); vẫn cần một root funding account. Premature — hợp lý ở Phase 2.

## Decision outcome

Chọn **Option A — `SYSTEM_FUNDING`**. Đây là cách duy nhất giữ double-entry không exception. Balance âm chính là ý nghĩa của account (tổng platform liability). Withdrawal (Phase 2) đi chiều ngược: `DEBIT user / CREDIT SYSTEM_FUNDING`.

## Consequences

**Positive:** trial balance luôn cân; reconciliation check mạnh + đơn giản; migration Phase 2 chỉ thêm external reference.
**Negative:** phải giải thích balance âm; phải seed `SYSTEM_FUNDING` trong migration (khi top-up land).
**Neutral:** Phase 2 có thể split thành nhiều funding account mà không đổi user-facing model.

## Risks & open questions

- **Authorization**: chỉ internal top-up service được tạo transaction DEBIT `SYSTEM_FUNDING`, không phải user request trực tiếp — enforce ở service layer.
- **Hot account**: nhiều top-up đồng thời cùng DEBIT `SYSTEM_FUNDING` → xem hot-account-problem; cân nhắc optimistic locking có đủ hay cần async.

## References

- [ADR-0005](0005-ledger-model.md) — double-entry append-only
- [ADR-0010](0010-aggregate-boundary.md) — Account aggregate; SYSTEM_FUNDING có `owner_ref` null
- Source analysis: `Research/ledger-system-ADR/wiki/adr-009-system-funding-account.md`
- Vault wikis: `double-entry-bookkeeping`, `accounting-transaction`, `hot-account-problem`
