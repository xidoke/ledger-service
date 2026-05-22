---

status: Accepted
date: 2026-05-20
----------------

# ADR-0017: Observability — Structured JSON log + MDC correlation id + Spring Actuator

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-23 từ vault source `Research/ledger-system-ADR/wiki/adr-017-observability.md`, phản ánh cơ chế đã land trong code Phase 0 (xem §Cập nhật triển khai — vault giả định `logstash-logback-encoder`, thực tế dùng structured logging built-in của Spring Boot 3.5).

## Context

Ledger Service là hệ thống tài chính — khi có sự cố production (balance drift, transaction fail, outbox event stuck), debug nhanh là **critical**: mỗi phút không tìm ra root cause là thêm rủi ro cho tiền của user. Ngay cả ở dạng monolith một server, hai vấn đề observability cơ bản vẫn xảy ra:

1. **Traceability across layers** — một request HTTP → service → repository sinh hàng chục log line; không có shared identifier xuyên suốt thì không biết line nào thuộc request nào, nhất là khi nhiều request concurrent.
2. **Log không machine-parseable** — flat text (`2026-05-20 INFO Processing transfer abc`) khó aggregate/search trên server thật; ELK/Loki và `grep` đều kém hiệu quả.

Health check + metrics là requirement vận hành cơ bản (load balancer / k8s probe cần `/health`), không phải nice-to-have. Quyết định hôm nay phải **không gãy khi lên Nấc 2 multi-service** — structured log + correlation id là foundation của distributed tracing sau này.

## Decision drivers

- **Debug được trên server thật** — log đủ context để trace request đầu→cuối, không cần reproduce locally.
- **Không gãy khi lên Nấc 2** — structured JSON + correlation id là precondition của OpenTelemetry, không phải thay toàn bộ khi scale.
- **Overhead thấp, setup đơn giản** — monolith, team nhỏ, không thêm infra phức tạp.
- **Align với idempotency** — `correlationId` từ HTTP header propagate vào mọi log line để trace retry path (liên kết ADR-0012 idempotency, chưa distilled — M3).

## Considered options

### Option A — Structured JSON log + MDC correlation id + Spring Actuator (CHỌN)

JSON log (mỗi line một object machine-parseable) + MDC propagate correlation id tại servlet filter (header `X-Correlation-Id` nếu hợp lệ, không thì UUID mới) + Actuator expose health/info/metrics.
**Pros:** mọi log line của một request share correlation id → filter ra đúng context; JSON ingest thẳng vào ELK/Loki/CloudWatch không cần preprocess; MDC transparent với application code; Actuator zero-config cho probe; không thêm infra mới.
**Cons:** JSON khó đọc trực tiếp trên terminal (cần `jq`); MDC không tự propagate sang `@Async`/`CompletableFuture` thread (cần `TaskDecorator` thủ công).

### Option B — Flat text log + grep

Logback mặc định Spring Boot.
**Pros:** zero config; đọc thẳng trên terminal.
**Cons:** không machine-parseable (không aggregate field cụ thể được); không có correlation id built-in → không trace xuyên layer; ELK phải parse text → brittle. **Loại** vì không đáp ứng yêu cầu debug production.

### Option C — Full distributed tracing với OpenTelemetry (Jaeger/Zipkin)

**Pros:** trace end-to-end với span visualization; industry standard cho microservices.
**Cons:** overhead infra đáng kể (collector + backend) — không cần cho monolith; complexity cao. **Hoãn tới Nấc 4** — KHÔNG loại hẳn: structured log + correlation id là bridge, OpenTelemetry là destination (correlation id map thẳng sang `trace_id`).

## Decision outcome

Chọn **Option A** — minimal viable observability đủ debug production incident trên monolith, đồng thời là foundation cho distributed tracing. OpenTelemetry (C) hoãn có chủ đích: correlation id trong MDC là precondition, khi upgrade map trực tiếp sang `trace_id` mà không rethink architecture.

## Cập nhật triển khai (2026-05-23, Phase 0)

Cơ chế **thực tế đã land** khác sketch trong vault (vault giả định `logstash-logback-encoder` + `logback-spring.xml`):

- **Structured logging = built-in Spring Boot 3.5**, KHÔNG cần `logstash-logback-encoder` hay `logback-spring.xml`. Cấu hình bằng `logging.structured.format` trong `application.yml`; output theo **ECS** (Elastic Common Schema, `"ecs":{"version":"8.11"}`). Đây là drift implementation-detail — *quyết định* (structured JSON + MDC + Actuator) giữ nguyên, chỉ *cơ chế* đơn giản hơn nhờ Spring Boot 3.4+ hỗ trợ native. (Vault là decision-log append-only nên không sửa; reconciliation ghi tại đây — đúng cách [ADR-0019](0019-ddd-tactical-patterns.md) xử lý.)
- **MDC key là `correlationId`** (camelCase), không phải `correlation_id` như snippet vault.
- **`CorrelationIdFilter`** (`common/web`): `OncePerRequestFilter`, `@Order(HIGHEST_PRECEDENCE)`. Header client-controlled được **validate qua allowlist `[A-Za-z0-9_-]{1,64}`** trước khi reflect vào response/MDC — chốt lỗ HTTP response-splitting / log-injection (vault không đề cập; bổ sung khi land). `MDC.remove` trong `finally` để pooled thread không leak.
- **Actuator** đã restrict đúng: chỉ expose `health, info, metrics` (loại `env`/`beans`/`heapdump` như Risk vault nêu); `build-info.properties` cho `/actuator/info` báo version + build time.
- **Chưa land** (đúng phạm vi — sẽ theo module tương ứng): custom outbox-lag `HealthIndicator` (M5 outbox), MDC `TaskDecorator` cho `@Async` (chưa có async path), business-context log statement cho transfer/reconciliation (theo từng feature). "Verify correlation id propagate qua transfer/topup path" thuộc LDG-58.

## Consequences

**Positive:** filter log theo `correlationId` ra đúng context của một request; log ECS sẵn sàng cho ELK/Loki không preprocess; health endpoint sẵn cho load balancer / k8s; foundation cho OpenTelemetry không cần rework.

**Negative:** dev local phải `jq` để đọc JSON (giảm bằng pattern encoder ở local profile); MDC không tự sang `@Async` thread; log volume tăng nhẹ so với flat text (không đáng kể ở scale này).

**Neutral:** structured logging built-in của Spring Boot là library ổn định, không phải risk mới; Actuator cần review security để không expose endpoint nhạy cảm (đã làm — chỉ 3 endpoint).

## Risks & open questions

- **Log volume growth** — review log level (INFO vs DEBUG); tránh log trong hot path (vd mỗi `ledger_entry` read trong reconciliation query).
- **MDC async leakage** — nếu thread pool reuse mà thiếu `MDC.remove`, correlation id request cũ leak sang request mới. Đã chốt `finally { MDC.remove }` trong filter.
- **Actuator security** — `/actuator/env` lộ env var (gồm DB password) nếu bất cẩn; đã restrict về `health,info,metrics`.
- **Open question** — khi nào cần dashboard riêng (ELK/Grafana Loki)? Hiện `grep`/`jq` qua SSH đủ; hoãn tới khi cần.

## References

- [ADR-0019](0019-ddd-tactical-patterns.md) — tiền lệ ghi "Cập nhật triển khai" reconcile vault-vs-code
- [ADR-0004](0004-package-structure.md) — `CorrelationIdFilter` ở `common/web` (shared infra, không phải feature)
- Source analysis: `Research/ledger-system-ADR/wiki/adr-017-observability.md`
