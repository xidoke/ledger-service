---

status: Accepted
date: 2026-05-20
----------------

# ADR-0008: Currency Scope — Single Currency ở Nấc 0-1

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-23 từ vault source `Research/ledger-system-ADR/wiki/adr-008-currency-scope.md`.

## Context

Ledger Service ở Phase 0-1 cố ý KHÔNG làm đa tiền tệ / FX. Nhưng "chưa làm" cần một quyết định tường minh: nếu coi multi-currency là "sẽ làm sau" mà không chốt, dev dễ build coupling khó tháo (hardcode currency ở display, bỏ cột `currency`, viết arithmetic không nhận currency parameter) — nợ này đắt khi mở rộng ở Phase 2.

## Decision drivers

- **Scope discipline**: multi-currency thêm complexity lớn (rate table, conversion rounding, dual-currency invariant) không tương xứng giá trị ở phase này.
- **Đúng problem trước**: correctness core ledger (double-entry, idempotency, concurrency) > currency breadth.
- **Forward compatibility**: không đóng cửa tương lai — phải có migration path additive.
- **Không over-engineer** (YAGNI).

## Considered options

### Option A — Single currency, cấu hình lúc startup (chọn)

Một currency duy nhất cấu hình qua env (`LEDGER_CURRENCY`); mọi account cùng currency. Schema **vẫn giữ cột `currency`** trên `accounts` (semantic clarity + chỗ trống cho tương lai); validation reject request có currency khác.

**Pros:** không cần FX rate/conversion/dual-currency invariant; tập trung correctness; migration tương lai chỉ relax constraint + add rate table (additive); khớp [[ADR-0007](0007-money-representation.md)] (BIGINT minor units, scale theo ISO 4217).
**Cons:** không demo được multi-currency; cross-currency transfer cần ADR FX riêng sau.

### Option B — Multi-currency từ đầu

Mỗi account currency riêng, bảng `fx_rates`, validation cross-currency, FX P&L account.

**Pros:** không cần migration sau; demo đa dạng hơn.
**Cons:** complexity tăng mạnh ngay (rate table, conversion rounding, `Σ DEBIT == Σ CREDIT` không còn đúng trên số nguyên thô khi khác currency → phải check per-currency, cần account `FX_GAIN_LOSS`); rủi ro làm sai core mechanics vì sa đà currency edge case. Loại.

## Decision outcome

Chọn **Option A**. Currency cố định qua config; cột `currency` có sẵn từ đầu (không hardcode đóng cửa). Multi-currency + FX để ADR riêng ở Phase 2 (rate table, conversion, FX invariant).

## Consequences

**Positive:** invariant `Σ DEBIT == Σ CREDIT` là phép integer đơn giản, không cần logic currency-aware; không cần FX infra.
**Negative:** cross-currency transfer bị reject ở Phase 0-1; khi PV hỏi multi-currency cần giải thích scope discipline.
**Neutral:** currency code chọn lúc deploy; BIGINT representation đúng cho mọi currency theo ISO 4217 exponent.

## Risks & open questions

- **Upgrade path Phase 2**: account cũ giữ currency cũ (OK); account đổi currency là migration phức tạp → ADR riêng.
- **Cross-currency internal transfer / rate provider**: FX operation, để dành Phase 2.

## References

- [ADR-0005](0005-ledger-model.md) — ledger model
- [ADR-0007](0007-money-representation.md) — BIGINT minor units, scale theo currency
- Source analysis: `Research/ledger-system-ADR/wiki/adr-008-currency-scope.md`
- Vault wikis: `money-representation`, `rounding-in-money`
