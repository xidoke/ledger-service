---

status: Accepted
date: 2026-05-20
----------------

# ADR-0012: Idempotency — `Idempotency-Key` header + `idempotency_keys` table

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-23 từ vault source `Research/ledger-system-ADR/wiki/adr-012-idempotency.md`, phản ánh cơ chế đã land trong code (LDG-48 table + filter, LDG-49 require + in-flight, LDG-50 concurrency proof — xem §Cập nhật triển khai).

## Context

Hai write endpoint chuyển tiền thật — `POST /accounts/{id}/topups` và `POST /transfers` — phải an toàn khi client retry. Mạng đứt sau khi server commit nhưng trước khi client nhận response là chuyện thường (proxy, load balancer, mobile, hoặc user bấm nút 2 lần). Không có cơ chế bảo vệ → double top-up / double transfer: tiền nhân đôi hoặc mất. Hệ thống cần **exactly-once execution** cho money operation: dù request đến bao nhiêu lần với cùng intent, chỉ một transaction được tạo và một kết quả được trả về.

## Decision drivers

- **Correctness tuyệt đối với tiền** — double-charge/double-transfer là lỗi nghiêm trọng nhất, không chấp nhận dù xác suất thấp.
- **Client không tin cậy được** — proxy/LB/mobile retry không hỏi server; server phải tự bảo vệ (defense in depth).
- **Bắt lỗi client** — cùng key nhưng khác body cần được phát hiện, không silent-accept.
- **Concurrent same-key** — hai request đồng thời cùng key phải chỉ chạy một lần (race).
- **Mô hình đơn giản** — một bảng DB làm source of truth, không thêm Redis/cache.

## Considered options

### Option A — Header `Idempotency-Key` + bảng `idempotency_keys` (CHỌN)

Client sinh UUID/ULID, gửi qua header `Idempotency-Key`. Server lookup theo key (PK); miss → chạy + lưu response; hit + cùng `request_hash` → replay cached response; hit + khác hash → 422; hit đang xử lý → 409; thiếu key trên money endpoint → 400.
**Pros:** giải quyết hoàn toàn double-execute; single source of truth tại DB; `request_hash` bắt lỗi client; tương thích retry của optimistic-lock (ADR-0011); pattern Stripe chứng minh trong production.
**Cons:** mỗi write thêm 1 lookup + 1 write (overhead nhỏ); cần chọn TTL cẩn thận + cleanup job; concurrent same-key cần xử lý đặc biệt.

### Option B — Không idempotency, dựa client guard

**Cons:** client không kiểm soát proxy/LB retry; mobile không tin cậy; với tiền, một lần double-transfer là không chấp nhận. **Loại** — vi phạm defense-in-depth.

### Option C — Client-generated `transaction.id` làm PK (kiểu TigerBeetle)

Unique constraint trên `transactions.id` tự chặn duplicate.
**Cons:** không lưu `request_hash` → không bắt được "cùng key khác body"; không phân biệt "đang xử lý" vs "đã xong"; không lưu `response_body` → phải re-query để build response; lệch HTTP convention (`Idempotency-Key` là chuẩn nhiều client SDK hỗ trợ sẵn). **Loại** — thiếu 2/3 semantics.

## Decision outcome

**Chọn Option A.** Cho đầy đủ semantics: detect "đang xử lý" (409), detect "cùng key khác body" (422), lưu cached response để replay. Header `Idempotency-Key` là HTTP convention chuẩn. Hai money endpoint **bắt buộc** header (thiếu → 400); endpoint read-only không cần.

## Cập nhật triển khai (2026-05-23, LDG-48/49/50)

Cơ chế **đã land** tinh chỉnh sketch trong vault ở vài điểm quan trọng:

- **Claim-first, commit TÁCH RIÊNG (không cùng một transaction với business logic).** Vault gợi ý ghi `idempotency_keys` + business trong cùng một DB transaction. Nhưng để một request đồng thời *thấy* được trạng thái "đang xử lý", row PENDING phải **đã commit** trước khi business chạy — một row chưa commit là vô hình dưới `READ_COMMITTED`. Nên flow thực tế: `INSERT … ON CONFLICT (key) DO NOTHING` (PENDING, commit ngay) → chạy business (transaction riêng của service) → `UPDATE … COMPLETED` (commit). Atomic `INSERT … ON CONFLICT` chính là điểm serialize concurrent same-key (đúng tinh thần [ADR-0006](0006-balance-representation.md)/double-entry consistency, và rẻ hơn `SELECT FOR UPDATE`/nâng isolation).
- **Cột `status` tường minh** (PENDING/COMPLETED) thay vì suy ra in-flight từ `response_status IS NULL` — rõ ràng hơn khi đọc.
- **Status taxonomy** (khớp [ADR-0018](0018-hexagonal-architecture.md) error mapping): thiếu key → **400**; concurrent in-flight (PENDING) → **409**; cùng key khác body → **422** (KHÔNG 409 — 422 = client-contract violation, để 409 riêng cho concurrency conflict). Filter chạy trước DispatcherServlet nên tự ghi `problem+json`.
- **Chỉ lưu response 2xx**; op fail (non-2xx hoặc throw) → `release` (DELETE PENDING) để client retry được.
- **Cột**: `response_body` dùng `TEXT` (không JSONB); migrations V6 (table) + V7 (status + nullable response).
- **In-flight = fail-fast 409, không block-then-replay**: kẻ thua claim trả 409 ngay (client retry sau), không chờ winner xong rồi replay. Exactly-once vẫn đảm bảo (side effect chạy 1 lần) — chứng minh bằng `IdempotencyConcurrencyTest` (2 luồng cùng key → balance đổi đúng 1 lần).

Hiện thực: `idempotency/` (filter `OncePerRequestFilter` + `IdempotencyJdbcStore`). Là cross-cutting infra nên dùng cấu trúc gọn (ADR-0018 pragmatic exception), không aggregate.

## Consequences

**Positive:** retry an toàn tuyệt đối cho client + proxy; tương thích optimistic-lock retry (ADR-0011); `request_hash` bắt lỗi client; cached response phục vụ audit.
**Negative:** mỗi write thêm lookup + write (~1-2ms local); cần cleanup job purge key hết TTL; client phải sinh + gửi key.
**Neutral:** key scoped per endpoint là best practice; TTL mặc định 24h (Stripe), configurable — chưa enforce (xem Risks).

## Risks & open questions

- **Orphaned PENDING khi crash mid-flight**: nếu process chết sau claim, trước complete/release → row PENDING kẹt → 409 vĩnh viễn cho key đó. Cần TTL/reaper sweep (Stripe 24h) — đã capture là follow-up (LDG-67); `created_at` được ghi sẵn cho việc này.
- **Key không ép format**: validate max length 255, không ép UUID — đủ cho Nấc 0-1.
- **`response_body` lớn**: TTL 24h + traffic Nấc 0-1 không phải vấn đề; Nấc 2+ review storage policy.

## References

- [ADR-0011](0011-concurrency-strategy.md) — optimistic-lock retry dùng cùng key
- [ADR-0006](0006-balance-representation.md) — balance cache commit cùng transaction với entries
- [ADR-0018](0018-hexagonal-architecture.md) — error→HTTP status taxonomy; idempotency là infra pragmatic exception
- Source analysis: `Research/ledger-system-ADR/wiki/adr-012-idempotency.md`; pattern: `Research/concurrent-api-design/wiki/idempotency-keys-stripe-pattern.md`
