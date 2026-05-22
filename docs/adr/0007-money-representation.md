---

status: Accepted
date: 2026-05-20
----------------

# ADR-0007: Money Representation — BIGINT lưu đơn vị nhỏ nhất

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-22 từ vault source `Research/ledger-system-ADR/wiki/adr-007-money-representation.md`.

## Context

Mọi hệ thống tài chính đều phải quyết định cách biểu diễn tiền tệ trong code và database. Ledger Service ở Phase 0-1 xử lý một đơn vị tiền tệ duy nhất (currency scope quyết định ở ADR-0008, Phase 1), nhưng quyết định representation vẫn ảnh hưởng đến tính chính xác, hiệu năng và độ phức tạp vận hành lâu dài.

Sai lầm phổ biến nhất của developer không có nền kế toán: dùng `float` hoặc `double` để lưu tiền. IEEE 754 floating-point là chuẩn binary, không phải decimal. `0.1 + 0.2` trong binary là `0.30000000000000004` — không phải lỗi của Java hay PostgreSQL, mà là hành vi đúng của IEEE 754 khi biểu diễn phân số thập phân trong hệ nhị phân. Với hệ thống ledger xử lý hàng triệu bút toán, các sai số nhỏ này tích lũy thành chênh lệch đáng kể và không thể giải thích.

Ledger Service sử dụng Java 21 ([ADR-0002](0002-language-framework.md)) và PostgreSQL ([ADR-0003](0003-database.md)). Cả hai đều hỗ trợ nhiều numeric type khác nhau. Quyết định này cần balance giữa correctness, performance, và simplicity cho Phase 0-1.

## Decision drivers

- **Correctness tuyệt đối**: không có rounding errors hay floating-point artifacts trong bất kỳ phép tính nào. `Σ DEBIT == Σ CREDIT` phải là phép bằng chính xác theo bit, không phải xấp xỉ.
- **Đơn giản**: developer không cần nhớ nhiều quy tắc đặc biệt khi viết arithmetic code.
- **Hiệu năng**: lưu trữ và tính toán phải nhanh — đặc biệt quan trọng khi reconciliation job aggregate hàng triệu entries.
- **Đủ dung lượng**: phải cover mọi amount hợp lý trong domain e-wallet.
- **Rõ ràng về scale**: mỗi currency cần biết "đơn vị nhỏ nhất" là gì — phải có nơi document rõ.

## Considered options

### Option A — BIGINT lưu đơn vị nhỏ nhất (integer minor units) — lựa chọn chính

Lưu tất cả monetary amounts dưới dạng `BIGINT` (64-bit signed integer) trong PostgreSQL, tương ứng `long` trong Java. Giá trị biểu diễn đơn vị nhỏ nhất của currency theo ISO 4217.

- VND (Vietnam Dong): exponent = 0, không có subunit, 1 đồng = 1 đơn vị lưu trữ. `100,000 VND → 100000`.
- USD: exponent = 2, 1 dollar = 100 cents. `$12.34 → 1234`.
- JPY: exponent = 0, `¥500 → 500`.

Arithmetic hoàn toàn integer: cộng, trừ, so sánh đều exact. Phép chia chỉ xảy ra tại display layer (convert về major unit để hiển thị) và phải xử lý explicit remainder.

PostgreSQL `BIGINT` (signed 64-bit): range −9,223,372,036,854,775,808 đến +9,223,372,036,854,775,807. Tính theo USD cents: đủ cover ~$92 quadrillion — không có ứng dụng e-wallet nào cần nhiều hơn.

**Pros:**

- Integer arithmetic là exact — không có floating-point approximation.
- `SUM(amount)` trên millions of entries: PostgreSQL thực hiện integer addition — nhanh và chính xác.
- Overflow detection đơn giản: Java `Math.addExact()` throw `ArithmeticException` khi overflow — không có silent wrapping.
- Không có thư viện phụ cần thêm vào; `long` là primitive type.
- Comparison (`>`, `<`, `==`) exact, có thể dùng làm database constraint trực tiếp.

**Cons:**

- Mọi business logic phải hiểu và nhớ rằng amount đang ở đơn vị nhỏ nhất. Cần naming convention rõ (ví dụ: field name `amountMinorUnits` hoặc comment ở VO class).
- Không thể lưu amount có phần lẻ nhỏ hơn đơn vị nhỏ nhất (ví dụ: 0.001 VND) — nhưng đây không phải requirement thực tế ở Phase 0-1.
- Scale per-currency phải được document rõ ràng và nhất quán; nhầm scale giữa currencies sẽ gây lỗi nghiêm trọng.

### Option B — PostgreSQL NUMERIC(19,4)

`NUMERIC(precision, scale)` lưu decimal chính xác — không phải floating-point. `NUMERIC(19,4)` lưu tối đa 19 chữ số tổng, 4 chữ số sau dấu thập phân.

**Pros:**

- Exact decimal, không có binary approximation.
- Human-readable khi query trực tiếp trong database: `12.3400` đọc rõ hơn `1234`.
- Phù hợp cho FX calculations có nhiều chữ số thập phân.

**Cons:**

- Chậm hơn BIGINT: PostgreSQL phải dùng variable-length numeric engine cho NUMERIC, trong khi BIGINT là native 8-byte integer — arithmetic và comparison chậm hơn đáng kể.
- Storage lớn hơn: NUMERIC dùng variable bytes (2 bytes per 4 digits) so với fixed 8 bytes của BIGINT.
- `SUM` trên NUMERIC cũng chậm hơn so với integer sum, ảnh hưởng reconciliation job.
- Vẫn cần convention về scale (4 chữ số sau dấu thập phân là bao nhiêu "cents"?). Không giải quyết được vấn đề conceptual của Option A.

### Option C — Java BigDecimal ánh xạ tới NUMERIC

Dùng `java.math.BigDecimal` ở application layer, ánh xạ vào PostgreSQL `NUMERIC`.

**Pros:**

- Exact decimal arithmetic trong cả Java và DB.
- API quen thuộc với developer kế toán, financial.

**Cons:**

- `BigDecimal` là object type, không phải primitive — GC pressure cao khi xử lý hàng triệu entries.
- `BigDecimal` comparison phải dùng `compareTo()`, không phải `==` — dễ bug nếu developer dùng `equals()` (khác cả về scale: `2.0.equals(2.00) == false`).
- Hiệu năng kém nhất trong ba options cho high-throughput path.
- Vẫn cần discipline về scale rounding mode (`MathContext`).

## Decision outcome

Chọn **Option A — BIGINT lưu integer minor units**. Toàn bộ cột `amount` trong `ledger_entries` và `balance` trong `accounts` đều là `BIGINT` ở PostgreSQL, ánh xạ vào `long` trong Java. Giá trị biểu diễn đơn vị nhỏ nhất của currency theo ISO 4217 exponent.

Lý do: correctness và performance cùng lúc, với chi phí complexity thấp nhất. Integer arithmetic là exact, nhanh, và không cần thư viện phụ. NUMERIC và BigDecimal exact nhưng chậm hơn mà không mang lại benefit gì thêm cho domain e-wallet đơn giản ở Phase 0-1. Floating-point bị loại hoàn toàn vì vi phạm correctness requirement.

ISO 4217 exponent table là reference duy nhất cho scale per currency. Ở Phase 0-1 với single currency (VND hoặc USD), scale là hằng số tại compile time và phải được document trong `CurrencyConfig` class.

## Consequences

**Positive:**

- `Σ DEBIT == Σ CREDIT` là integer equality — không có epsilon so sánh, không có rounding tolerance.
- Performance reconciliation job tốt nhất có thể: `SELECT SUM(amount)` trên BIGINT là native integer addition trong PostgreSQL.
- Overflow protection: dùng `Math.addExact()` hoặc `Math.subtractExact()` trong Java để throw exception thay vì silent wrap.

**Negative:**

- Phải document rõ currency exponent và convention minor-units trong code. Nếu thiếu document, bug do nhầm unit (ghi `100` thay vì `10000` cho $100) khó debug.
- Display layer phải convert: `10000 / 100.0` để hiển thị `$100.00`. Cần format utility dùng chung — không để logic này rải rác.

**Neutral:**

- Nếu tương lai mở rộng sang multi-currency với các currency exponent khác nhau, phải thêm bảng `currencies` và đọc exponent dynamically. Đây là thay đổi có thể làm sau mà không cần đổi storage type.

## Risks & open questions

- **VND không có subunit**: nếu dùng VND, `amount` trong BIGINT là số nguyên đồng — không có cents. Convention phải clear: `1000` nghĩa là 1,000 VND. Khi sau này thêm USD (exponent = 2), phải rõ ràng không thể compare raw integer amounts giữa hai currency.
- **Rounding khi split**: nếu split amount cho nhiều recipients, remainder phải được allocated explicit. Không bao giờ dùng `amount / n` với integer division mà discard remainder.
- **Display format**: cần `MoneyFormatter` utility class có unit test — không để `/ 100` rải rác trong code.

## References

- [ADR-0002](0002-language-framework.md) — Java `long` primitive type cho amount
- [ADR-0003](0003-database.md) — PostgreSQL BIGINT native 8-byte integer
- [ADR-0005](0005-ledger-model.md) — `amount` field trong ledger_entries dùng BIGINT
- Source analysis: `Research/ledger-system-ADR/wiki/adr-007-money-representation.md`
- Vault wikis referenced: `money-representation`, `rounding-in-money`
