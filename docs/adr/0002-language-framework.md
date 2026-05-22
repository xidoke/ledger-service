---

status: Accepted
date: 2026-05-20
----------------

# ADR-0002: Ngôn ngữ và framework — Java 21 + Spring Boot 3

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-22 từ vault source `Research/ledger-system-ADR/wiki/adr-002-language-framework.md`.

## Context

Ledger Service cần một ngôn ngữ + framework hỗ trợ tốt cho financial backend: transaction management, optimistic locking, ORM mature, và ecosystem có precedent cho ledger/payment systems. Stack phải fit với Phase 2 service split (xem [ADR-0001](0001-architectural-style.md)) — tức là không lock-in một runtime hay framework khó migrate.

Ràng buộc: solo deployment Phase 0-1, hạ tầng tối thiểu (Docker Compose local), nhưng phải production-quality patterns cho concurrency và ACID. Stack được chọn cũng định hình các quyết định downstream (ORM strategy, migration tool, observability stack).

## Decision drivers

**1. Mature ecosystem cho financial backend.** Spring Boot 3 + Spring Data JPA + Spring Transaction Management là bộ toolkit tiêu chuẩn cho enterprise/financial Java backend, có precedent đầy đủ ở các production ledger system. `@Transactional`, `@Version` (Hibernate optimistic locking), Spring Retry — tất cả là first-class primitives. Không cần reinvent transaction management hay optimistic locking từ đầu.

**2. Optimistic locking native qua Hibernate `@Version`.** Optimistic locking là critical requirement của ledger service ([ADR-0011](0011-concurrency-strategy.md), Phase 1). Hibernate `@Version` annotation tích hợp sẵn version-based optimistic locking vào entity layer — chỉ cần một annotation, framework handle conflict detection và throw `ObjectOptimisticLockingFailureException`. Các stack khác yêu cầu implement thủ công.

**3. ACID transaction management đơn giản.** `@Transactional` của Spring là abstraction đủ mạnh để đảm bảo `INSERT INTO ledger_entries` và `UPDATE accounts SET balance = ...` nằm trong cùng một database transaction. Đây là cornerstone của [ADR-0001](0001-architectural-style.md) — không có dual-write problem vì cùng một JDBC transaction bao phủ cả hai write.

**4. Java 21 Virtual Threads cho concurrency.** Java 21 Virtual Threads (Project Loom, stable) cho phép thread-per-request model đơn giản với overhead thấp hơn nhiều so với OS threads. Không cần học/áp dụng reactive (Reactor/WebFlux) cho concurrency-heavy paths ở scale Phase 0-1.

## Considered options

### Option A: Java 21 + Spring Boot 3 (LỰA CHỌN)

Java 21 LTS với Spring Boot 3.x. Spring Data JPA (Hibernate) cho ORM. Spring Transaction Management. Spring Web MVC (blocking, với Virtual Threads). Flyway cho database migration.

**Pros:**

- Driver 1: mature ecosystem cho financial backend với precedent đầy đủ.
- Driver 2 & 3: `@Version` và `@Transactional` là native primitives — ít code thủ công cho concurrency control.
- Driver 4: Java 21 Virtual Threads cho thread-per-request model đơn giản, overhead thấp hơn OS threads.
- Flyway: migration-as-code, tích hợp Spring Boot autoconfigure, chạy tự động khi startup.
- Spring Boot Actuator: health check, metrics endpoint sẵn có — không cần viết từ đầu.
- Ecosystem mature: Spring Security (nếu cần auth), Spring Retry (cho retry loop sau optimistic lock conflict).

**Cons:**

- Verbosity cao hơn Go và Node.js đáng kể — boilerplate DTO, entity, repository, service layers.
- JVM memory footprint lớn hơn Go hay Node (~256MB vs ~20MB idle). Không phải vấn đề ở Phase 0-1 nhưng là chi phí thực nếu deploy cloud constrained-RAM.
- Learning curve khi đến từ scripting language: generics, checked exceptions, build system (Maven), module system.
- Startup time chậm hơn Go hay Node — ảnh hưởng đến dev feedback loop.

### Option B: Go + Gin (hoặc Chi, Fiber)

Go với một trong những HTTP framework phổ biến. GORM hoặc `sqlx` cho database access. Không có ORM-level optimistic locking sẵn.

**Pros:**

- Compile đến binary nhỏ, memory footprint thấp (~10-20MB).
- Syntax đơn giản hơn Java — ít ceremony hơn.
- Concurrency model (goroutines) mạnh và dễ dùng hơn Java threads truyền thống (mặc dù Java 21 VT đã thu hẹp khoảng cách).
- Startup time nhanh.

**Cons:**

- Không có `@Transactional` hay `@Version` — phải implement optimistic locking thủ công qua `UPDATE ... WHERE version = :v AND id = :id` và kiểm tra `rowsAffected`. Không sai, nhưng thêm surface area cho lỗi.
- GORM không có first-class optimistic locking — phải tự quản lý version field trong query.
- Ít tài liệu về ledger/financial-specific patterns (optimistic locking, outbox, two-phase transfer) trong Go ecosystem so với Spring ecosystem.
- Nếu Phase 2 cần Spring Batch, Spring Integration, hoặc Spring Cloud (cho service mesh) — phải switch hoặc rewrite.

### Option C: Node.js + NestJS (hoặc Fastify)

Node.js với NestJS (decorator-based DI gần với Spring).

**Pros:**

- NestJS có decorator-based DI gần với Spring, structured tốt hơn Express.
- TypeScript + TypeORM có `@VersionColumn` cho optimistic locking — gần tương đương `@Version` của Hibernate.

**Cons:**

- JavaScript single-threaded event loop cần `async/await` ở mọi nơi — cognitive overhead khi viết transaction-heavy code với nested DB calls.
- Ecosystem cho ledger/financial patterns ở Node.js nhỏ hơn nhiều. Ví dụ: không có tương đương Spring Retry sẵn có cho retry loop tự động sau optimistic lock failure.
- Driver 1 (mature financial ecosystem) bị weakest trong 3 options.

## Decision outcome

**Chọn Option A: Java 21 + Spring Boot 3.**

Driver 1 (mature ecosystem cho financial backend) + Driver 2 + 3 (Hibernate `@Version` + Spring `@Transactional` native) cộng dồn lại làm Option A có ROI cao nhất ở scale này — ít surface area cho lỗi trong phần code quan trọng nhất (transaction management và concurrency control).

Go (Option B) là lựa chọn kỹ thuật tốt về nhiều mặt (memory, startup time, simplicity) nhưng thiếu first-class optimistic locking và Spring-grade transaction abstraction — phải implement thủ công.

Java 21 Virtual Threads (Driver 4) xóa đi nhược điểm cũ của Java trong reactive context — thread-per-request model đủ tốt ở scale Phase 0-1 với overhead thấp hơn nhiều so với OS threads.

## Consequences

**Positive:**

- `@Transactional` + `@Version` là native primitives — ít code viết thủ công hơn cho concurrency control.
- Spring Boot Actuator cung cấp health endpoint `/actuator/health` ngay từ dependency, không cần viết thêm.
- Virtual Threads (Java 21 `--enable-preview` hoặc stable trong 21+) cho phép thread-per-request đơn giản mà không sacrifice throughput.
- Flyway migration tích hợp Spring Boot: schema migration chạy tự động khi startup.

**Negative:**

- Verbose hơn đáng kể — mỗi layer (controller, service, repository, entity) cần file riêng với boilerplate nhất định.
- JVM warm-up: first request sau startup chậm hơn Go/Node.
- Build tool learning curve: Maven khác hoàn toàn với npm/yarn.
- Checked exceptions cần xử lý rõ ràng — có thể gây friction ban đầu.

**Neutral:**

- Spring Boot 3 yêu cầu Java 17+ minimum — Java 21 LTS là lựa chọn tự nhiên.
- Không ảnh hưởng đến quyết định database ([ADR-0003](0003-database.md)) hay package structure ([ADR-0004](0004-package-structure.md)).

## Risks & open questions

**Rủi ro 1: Surface area của Spring ecosystem lớn.** Mitigation: tập trung vào subset cần thiết cho Phase 0-1 — `@Transactional`, `@Version`, Spring Data JPA CRUD, `@RestController`. Module nào không dùng (Batch, Integration, WebFlux) thì không pull dependency.

**Rủi ro 2: JVM memory trên môi trường deploy rẻ.** Mitigation: Docker Compose ở Phase 0-1 chạy local. Khi deploy cloud, GCP Cloud Run với 512MB RAM là đủ cho Spring Boot. Tuning `-Xms` / `-Xmx` cho tight environments.

**Câu hỏi mở:**

- Spring Data JPA hay JOOQ cho query phức tạp? (JPA đủ cho Phase 0-1; JOOQ có thể cần ở Phase 2 nếu query analytics phức tạp.)

## References

- [ADR-0001](0001-architectural-style.md) — quyết định modular monolith phụ thuộc vào Spring `@Transactional` cho atomic writes
- [ADR-0003](0003-database.md) — Java + Spring Data JPA + PostgreSQL driver là stack tích hợp tốt
- Source analysis: `Research/ledger-system-ADR/wiki/adr-002-language-framework.md`
- Vault wikis referenced: `optimistic-locking-for-ledgers`, `lost-update-on-balance`, `dual-write-problem`
