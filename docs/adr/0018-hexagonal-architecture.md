---

status: Accepted
date: 2026-05-20
----------------

# ADR-0018: Hexagonal Architecture (Ports & Adapters) bên trong mỗi module

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-23 từ vault source `Research/ledger-system-ADR/wiki/adr-018-hexagonal-architecture.md`, phản ánh cấu trúc đã land trong code (LDG-63 `account/`, LDG-64 `ledger/` — xem §Cập nhật triển khai). Refine [ADR-0004](0004-package-structure.md).

## Context

[ADR-0004](0004-package-structure.md) chốt **package-by-feature**: mỗi bounded context (`account/`, `ledger/`, `topup/`, `transfer/`, `idempotency/`, `outbox/`) là một package độc lập. Đó là *module boundary* ở cấp codebase — nhưng chưa quy định cấu trúc **bên trong** mỗi module.

Cách tổ chức Spring Boot điển hình (flat, hoặc theo technical layer `controller/`/`service/`/`repository/`) làm domain logic trộn với framework concern: `@Service` import `JpaRepository` trực tiếp, controller trả về JPA entity, domain nhận `HttpServletRequest`. Hệ quả: test domain phải spin Spring + DB; swap infra (JPA → JDBC) buộc sửa business logic; ranh giới "logic tài chính thuần" vs "Spring plumbing" nhòe dần. Với một ledger nơi invariant `Σ DEBIT == Σ CREDIT` và `balance >= 0` là correctness requirement, đây là rủi ro thật.

## Decision drivers

- **Domain test được không cần Postgres** — business rule tài chính phải test ở mili-giây, không phải spin Testcontainer mỗi lần.
- **Swap infrastructure dễ** — port là hợp đồng cố định; adapter là chi tiết thay thế được (JPA hôm nay, JDBC/gRPC/cache về sau) mà domain không đổi.
- **Ranh giới rõ cho Phase 2 service-split** — tách module = "mang `domain/` đi + viết adapter mới", không phải đọc class-soup.
- **Không để framework annotation rò vào domain** — `@Entity`/`@Column` trên domain class trói domain vào Hibernate.
- **Chứa DDD tactical patterns** ([ADR-0019](0019-ddd-tactical-patterns.md)) — Aggregate/VO/Domain Event là plain Java; Hexagonal là container kiến trúc tự nhiên.

## Considered options

### Option A — Layered/flat bên trong mỗi feature

`@Entity` domain + `@Service` inject `JpaRepository` + `@RestController`, tất cả ngang hàng.
**Pros:** quen thuộc, ít file, ít boilerplate; phù hợp codebase rất nhỏ.
**Cons:** Hibernate annotation xâm nhập domain; service không test được không có Spring; swap infra buộc sửa service; invariant có thể bị bypass; service-split phải tách thủ công từng class. **Loại.**

### Option B — Hexagonal / Ports-and-Adapters cho mỗi module (CHỌN)

`domain/` (pure Java + port interface) · `adapter/in/` (controller) · `adapter/out/` (JPA/JdbcClient impl của port).
**Pros:** domain sạch framework → test mili-giây với in-memory mock; port là hợp đồng ổn định khi swap infra; dependency direction `adapter → domain` enforce được bằng ArchUnit; service-split = mang `domain/` + viết adapter mới.
**Cons:** nhiều file hơn ~30% (interface + JPA entity riêng + mapper); mapping boilerplate domain ↔ JPA entity; onboarding phải hiểu tại sao có hai class `Account`.

### Option C — Clean Architecture concentric (4 ring: entity/usecase/interfaceadapter/framework)

**Pros:** tách Entity vs Use Case rõ hơn; canonical Uncle Bob.
**Cons:** overkill ở scale ~5 use case — ranh giới Entity-ring vs Use-Case-ring mơ hồ, đẻ ra rất nhiều directory mà không thêm lợi ích so với Hexagonal 2-3 tầng. **Loại.**

## Decision outcome

Chọn **Option B**. Driver quyết định là Driver 1 (domain test được không cần Postgres): trong fintech, business rule là tài sản giá trị nhất — phải test nhanh, rẻ, tin cậy, và Hexagonal được thiết kế đúng để bảo vệ điều đó. Option A vi phạm "swap infra" + "framework rò vào domain"; Option C overhead không tương xứng ở Nấc 0-1.

## Cập nhật triển khai (2026-05-23, LDG-63 + LDG-64)

Cấu trúc **thực tế đã land** gọn hơn sketch trong vault (vault vẽ thêm `port/in` + `port/out` tách riêng):

- **`domain/`** — pure Java: aggregate (`Account`, `Transaction`), Value Object/exception, **và port interface luôn nằm ở đây** (`AccountRepository`, `TransactionRepository`, read-only `LedgerEntryQueryRepository`). KHÔNG có package `port/` tách riêng — gom port vào `domain/` tránh interface-proliferation ở scale này (đúng "Rủi ro 2" của vault).
- **`adapter/in/`** — `@RestController` + request/response DTO; không chứa business logic.
- **`adapter/out/`** — `AccountPersistenceAdapter`/`TransactionPostingAdapter` implement port; JPA entity riêng (`AccountJpaEntity`) map sang domain qua mapper; append path dùng `JdbcClient` (xem jdbcclient-vs-jpa).
- **Inbound port (`port/in` use-case interface)**: chưa dùng — service class được inject trực tiếp ở Nấc 0-1; thêm interface sau nếu cần mock phức tạp.
- **Enforcement đã bật, không còn "nếu cần"**: ArchUnit rule `domainIsFrameworkFree` (`LedgerArchitectureTest`) fail build nếu `..domain..` import `jakarta.persistence..` hoặc `org.springframework..`. `account/` (LDG-63) và `ledger/` (LDG-64) đều theo layout này.
- **Ngoại lệ thực dụng** (outbox/idempotency CRUD-thuần dùng cấu trúc đơn giản hơn) — chưa hiện thực; sẽ áp khi các module đó land (M3/M5).

## Consequences

**Positive:** unit test domain không cần Spring/DB (`AccountTest`, `TransactionTest` chạy mili-giây với in-memory); domain entity dễ đọc, không bị Hibernate annotation che; service-split Phase 2 chỉ cần mang `domain/` + viết adapter mới; vi phạm dependency-direction bị ArchUnit bắt tức thì trong CI.

**Negative:** nhiều file hơn ~30% (port + JPA entity + mapper mỗi aggregate); mapping boilerplate domain ↔ JPA entity phải maintain + dễ quên sync field; onboarding phải hiểu hai class `Account`.

**Neutral:** Spring DI inject adapter vào service qua port interface bình thường; integration test (Testcontainers) vẫn cần cho adapter layer — Hexagonal chỉ làm unit test domain nhanh hơn, không bỏ integration test.

## Risks & open questions

- **Mapping mất đồng bộ** — domain ↔ JPA entity là hai class; quên sync field khi thêm column. Mitigation: mapper roundtrip test, hoặc MapStruct compile-time check về sau.
- **Cross-module call** — khi `transfer` cần balance của account nguồn: gọi qua `account` public API (port/service), KHÔNG đâm thẳng vào adapter của module khác (giữ đúng ArchUnit feature-independence + use-case→core của [ADR-0004](0004-package-structure.md)/[ADR-0019](0019-ddd-tactical-patterns.md)).
- **Security identity** (khi thêm auth ở Nấc 1): adapter extract `UserId` từ `SecurityContext` và truyền xuống dưới dạng plain value object — domain không biết Spring Security tồn tại.

## References

- [ADR-0004](0004-package-structure.md) — package-by-feature là outer structure; ADR này quy định inner structure mỗi module
- [ADR-0019](0019-ddd-tactical-patterns.md) — Aggregate/VO sống trong `domain/`; Hexagonal là container cho DDD tactical
- [ADR-0010](0010-aggregate-boundary.md) — Account = locking unit; map vào `domain/Account` aggregate root
- Source analysis: `Research/ledger-system-ADR/wiki/adr-018-hexagonal-architecture.md`
