---

status: Accepted
date: 2026-05-20
----------------

# ADR-0016: Reconciliation — periodic drift job, alert-only

## Status

Accepted (2026-05-20). Distilled tới repo 2026-05-24 (LDG-56) từ vault source `Research/ledger-system-ADR/wiki/adr-016-reconciliation.md`.

## Context

`accounts.balance` là **cached projection** đọc nhanh; source of truth là append-only `ledger_entries` (ADR-0005, ADR-0006). Tách cache/ledger tạo khả năng **drift**: một bug (race, rounding, exception bị nuốt) có thể làm `balance` lệch khỏi `Σ entries` mà **không throw lỗi** — silent corruption, balance hiển thị sai. Modern Treasury document đúng class lỗi này ("a few million dollars had gone missing" — tiền không mất, chỉ mất *attribution*). Hệ thống đã có immutability (ADR-0005) nên balance **re-derive được bất cứ lúc nào** — câu hỏi còn lại: phát hiện drift khi nào, phản ứng ra sao.

## Decision drivers

- **Phát hiện silent bug sớm** — drift tích lũy nếu không kiểm tra định kỳ.
- **Immutable entries cho phép re-derive** — chỉ cần `SUM`, không cần snapshot.
- **Auto-correct là anti-pattern** — tự sửa balance ẩn đi bug thật; phải điều tra root cause trước.
- **Không overhead write path** — reconcile sau mỗi transaction là overkill + bottleneck.

## Considered options

- **A — Periodic `@Scheduled` job + drift report (CHỌN).** Định kỳ chạy 2 check, drift → log+alert, không sửa.
- **B — Không reconcile, tin ACID.** ACID chỉ đảm bảo trong-transaction; không bắt bug tính sai amount / drift tích lũy. Loại.
- **C — Continuous reconcile sau mỗi transaction.** Phát hiện tức thì nhưng `SUM` trên hot account ngày càng chậm → write-path bottleneck. Overkill Nấc 0-1.
- **D — Three-way với external (bank/PSP).** Bắt class lỗi khác (event lost) nhưng out-of-scope (chưa có payment gateway) — Nấc 2/3.

## Decision outcome

**Chọn A.** Cân bằng tốt nhất thoroughness ↔ cost cho Nấc 0-1: zero infra mới, không đụng write path, immutable entries → job idempotent chạy lại lúc nào cũng được.

## Trạng thái triển khai (2026-05-24, LDG-56)

`ReconciliationJob` (`ledger/adapter/in`, `@Scheduled(cron)` mặc định `0 0 2 * * *`, `@Transactional(readOnly)`), qua port `ReconciliationRepository` (`ledger/domain`) + JdbcClient adapter:

1. **Per-account drift**: `accounts.balance` vs `Σ(CREDIT +amount / DEBIT −amount)` (đúng sign convention codebase — khớp `accounts.balance`, KHÔNG phải DEBIT-positive như SQL phác trong vault), `HAVING` mismatch → chỉ trả account lệch.
2. **Trial balance**: `Σdebit − Σcredit` toàn hệ thống, phải = 0 (≠ 0 = tiền bị tạo/huỷ).

Drift → log **ERROR** (account, cached, ledger, drift) + Micrometer gauge `ledger.reconciliation.balance_drift_accounts` (Actuator `/metrics`). **Tuyệt đối không auto-correct** — operator điều tra, fix code, rồi nếu cần post correcting entry (ADR-0005), chạy lại reconcile xác nhận. Index `(account_id, created_at)` (V4) đủ cho GROUP BY ở Nấc 0-1.

## Consequences

**Positive:** safety net phát hiện silent corruption; idempotent read-only, không đụng write path; tạo culture data-quality sớm.
**Negative:** window phát hiện tối đa 1 ngày (chấp nhận Nấc 0-1); `SUM` tốn tài nguyên khi entries lớn (cần index/partition window sau); alert không có runbook → noise.
**Neutral:** có thể tăng frequency (hourly) không cần đổi architecture; three-way external = ADR mới khi có payment gateway.

## Risks & open questions

- **Performance `SUM`** ở triệu entries → cân nhắc partition-window (reconcile 30 ngày gần nhất vs snapshot) khi volume tăng.
- **Alert fatigue / runbook**: cần tách "đã biết đang fix" vs "mới phát hiện"; viết runbook khi alert wiring (Slack/email) land.
- **Near-realtime**: khi nào nâng tần suất — phụ thuộc volume + risk tolerance.

## References

- [ADR-0005](0005-ledger-model.md) — append-only entries: source of truth, re-derive được
- [ADR-0006](0006-balance-representation.md) — balance là cache cùng transaction với entries (cái mà job này đối chiếu)
- Source analysis: `Research/ledger-system-ADR/wiki/adr-016-reconciliation.md`; concept: `Research/ledger-systems/wiki/reconciliation.md`, `trial-balance.md`
