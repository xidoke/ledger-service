---

status: Accepted
date: 2026-05-20
----------------

# ADR-0015: Event Schema Versioning — explicit version field + tolerant reader

## Status

Accepted (2026-05-20). Distilled tới repo 2026-05-24 (LDG-57) từ vault source `Research/ledger-system-ADR/wiki/adr-015-event-schema-versioning.md`. Phản ánh tên đã ship (`schema_version`) và việc payload chưa được deserialize ở Nấc 0 — xem §Trạng thái triển khai.

## Context

Outbox events (ADR-0013) là **integration boundary** và **persist dài hạn**: một event ghi 2026 vẫn phải đọc được bởi code 2030 sau nhiều lần đổi schema. Đây là "phần khó nhất của event sourcing" (Greg Young): event là sự thật bất biến — không sửa/xoá được — nhưng nghiệp vụ thay đổi (thêm field, đổi cấu trúc, consumer mới cần thông tin event cũ không có). Không có chiến lược versioning từ đầu → mọi đổi schema thành breaking change buộc deploy đồng thời mọi consumer (bất khả thi), và tích luỹ tới "versioning bankruptcy". Project là modular monolith, KHÔNG Kafka/schema-registry → convention phải enforce bằng code review, không có tooling tự động.

## Decision drivers

- **Events bất biến** — code phải forward-compatible với schema cũ; không migrate được dữ liệu cũ.
- **Business sẽ đổi** — thêm field / event type mới là tất yếu.
- **Rolling deployment** — new + old code chạy song song; consumer mới đọc được event cũ và ngược lại.
- **Overhead thấp** — không Confluent/Avro ở quy mô này.
- **Detect breaking change sớm** + **DX rõ ràng** (thêm field không cần expert mỗi lần).

## Considered options

### Option A — Explicit version field + tolerant reader (CHỌN)

Mỗi event mang `event_type` (VARCHAR, vd `TransferPosted`) + `schema_version` (INT, bắt đầu `1`). Deserialize tolerant: Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` (field JSON mới mà DTO chưa có → ignore; field DTO thiếu trong JSON → null/default).

**Pros:** JSON human-readable (debug production bằng mắt); tolerant reader xử lý rolling deployment tự nhiên; `schema_version` là extension point cho upcaster; zero infra mới; convention đơn giản.
**Cons:** không có tooling enforce compat (dựa code review); semantic change (đổi *nghĩa* field) không để lại error signal — cần discipline.

### Option B — Không versioning

Tin schema không đổi, migrate mọi consumer cùng lúc (blue-green). **Loại:** sai thực tế (schema sẽ đổi); blue-green toàn bộ consumer bất khả thi khi service tăng; dẫn tới versioning bankruptcy. Rủi ro quá cao cho tiền.

### Option C — Schema Registry (Confluent/Avro)

Registry enforce compat (BACKWARD/FORWARD/FULL) + binary format. **Pros:** catch breaking change lúc publish, không lúc consumer crash. **Cons:** stateful HA service — overhead không tương xứng monolith + PostgreSQL-polling outbox; retrofit khó. **Hoãn**, không loại — đúng khi chuyển Kafka (Nấc 3/4).

## Decision outcome

**Chọn Option A.** Đơn giản nhất mà có plan versioning rõ từ ngày đầu, zero infra. Registry (C) re-evaluate khi: (a) >5 consumer, (b) cần binary throughput, hoặc (c) chuyển Kafka.

**Convention evolution (enforce qua code review):**

|         Loại thay đổi          |                                        Hành động                                         |
|--------------------------------|------------------------------------------------------------------------------------------|
| Thêm optional field            | Tăng `schema_version`, giữ `event_type`. Consumer cũ bỏ qua field mới (tolerant reader). |
| Thêm required field (breaking) | Tạo `event_type` mới (vd `TransferPosted_v2`), maintain handler cũ.                      |
| Đổi cấu trúc field             | `event_type` mới HOẶC tăng `schema_version` + viết upcaster.                             |
| **Đổi ngữ nghĩa field**        | **Bắt buộc `event_type` mới** — đây là *new fact*, không phải new version.               |
| Xoá field                      | Giữ trong DTO `@JsonIgnore`; log warning khi gặp.                                        |

**Hai quy tắc cứng:** (1) không bao giờ đổi tên JSON field (rename ở Java thì `@JsonProperty` giữ key); (2) không bao giờ đổi *nghĩa* field tại chỗ.

**Upcasting** (khi structural change cần làm sạch consumer code): transform tại read-time, dùng **linked-list pattern** (v1→v2→v3, mỗi hop chỉ biết convert sang version kế) — clean boundary, dễ test từng hop; tránh direct (v1→current) vì maintenance combinatorial. (Tránh async upcaster gọi external trong replay — N+1.)

## Trạng thái triển khai (2026-05-24)

- **Đã có:** cột `schema_version INT` trên `outbox` (Flyway V8) + `OutboxRecord.schemaVersion`; mọi event `append(…, 1)`. (Tên là `schema_version`, không phải `event_version` như phác trong vault — repo dùng tên đã ship.)
- **Tolerant reader:** Spring Boot mặc định `spring.jackson.deserialization.fail-on-unknown-properties=false` → đã thoả; chưa cấu hình tường minh vì **Nấc 0 chưa deserialize payload** (poller log raw JSON, chưa consumer parse). Khi consumer thật xuất hiện (Phase 2, LDG-78) → assert config này + áp convention trên.
- **Upcaster:** chưa có (chưa có version >1). `schema_version` là extension point sẵn sàng.

## Consequences

**Positive:** schema evolve không downtime / coordinated deploy; event store đọc được bởi code tương lai; JSON debug nhanh; convention rõ.
**Negative:** breaking change không tự detect — dựa code review; tích luỹ upcaster + handler song song theo thời gian.
**Neutral:** JSON verbose không đáng kể ở quy mô này; `schema_version` hiện ít dùng nhưng là extension point quan trọng.

## Risks & open questions

- **Semantic change nhầm thành schema change** — rủi ro cao nhất, không có error signal → checklist code review bắt buộc.
- **Behavioral versioning** — logic xử lý đổi dù schema không đổi: encode *kết quả* (vd `fee_amount`) vào event thay vì chỉ inputs, để downstream không phụ thuộc business logic hiện tại.
- **Move sang Kafka (Nấc 3+)** — có cần migrate outbox sang binary? Re-evaluate theo throughput lúc đó.

## References

- [ADR-0013](0013-event-publishing.md) — outbox: event_type + payload JSONB + schema_version mà ADR này versions
- Source analysis: `Research/ledger-system-ADR/wiki/adr-015-event-schema-versioning.md`
- Concepts: `Research/event-sourcing-pitfalls/wiki/{event-versioning-problem, weak-schema-and-tolerant-reader, upcasting, double-write-event-versioning}.md`
