---

status: Accepted
date: 2026-05-20
----------------

# ADR-0004: Cấu trúc package — Package-by-feature

## Status

Accepted (2026-05-20). Ported tới repo 2026-05-22 từ vault source `Research/ledger-system-ADR/wiki/adr-004-package-structure.md`.

## Context

Ledger Service là modular monolith ([ADR-0001](0001-architectural-style.md)). Cả codebase sống trong một JVM process, một deployment unit. Nhưng ADR-0001 đã xác định: module boundaries phải đủ cứng để Phase 2 có thể tách thành microservices mà không cần rewrite cấu trúc.

Câu hỏi cụ thể: trong một Spring Boot project, code nên được tổ chức như thế nào? Java cho phép nhiều cách — từ flat packages đến DDD Hexagonal Architecture phức tạp. Quyết định này ảnh hưởng trực tiếp đến: dễ đọc code, khả năng enforce module boundary, và chi phí tách service sau này.

Scope của codebase Phase 0-1: Account management, Transfer, Topup, LedgerEntry posting, Idempotency key tracking, Outbox event publishing, và Common utilities. Tổng cộng khoảng 30-50 class.

## Decision drivers

**1. Module boundaries phải phản ánh bounded contexts.** Phase 2 sẽ tách Ledger Service thành ít nhất: Account Service, Transfer Service, và có thể Notification Service. Nếu code của Account và Transfer trộn lẫn vào nhau (chia sẻ trực tiếp repository hoặc entity), việc tách sẽ đòi hỏi refactor lớn. Ranh giới package = ranh giới bounded context là nguyên tắc DDD thực tiễn.

**2. Dễ điều hướng và maintain.** Khi nhìn vào thư mục code, phải thấy ngay "tính năng này ở đâu" mà không cần nhớ convention layer. Cognitive overhead của việc phải tìm service class trong `service/`, entity trong `model/`, và repository trong `repository/` riêng biệt là chi phí thực ở mọi scale.

**3. Giảm coupling giữa modules.** Cross-package dependency là indicator của coupling. Nếu module `transfer` import trực tiếp entity của module `account`, đó là coupling cần cắt trước khi tách service. Package-by-feature làm điều này visible — IDE có thể báo khi có import vượt ranh giới module.

**4. Chuẩn bị cho changing aggregate boundaries.** Sai ranh giới aggregate từ đầu sẽ đắt để sửa — đây là pitfall đắt nhất trong event-sourced systems, nhưng nguyên lý áp dụng cho mọi kiến trúc. Package-by-feature buộc phải suy nghĩ rõ về ranh giới domain ngay từ khi viết class đầu tiên — một discipline tốt.

## Considered options

### Option A: Package-by-feature (LỰA CHỌN)

Tổ chức theo domain feature/bounded context:

```
com.xidoke.ledger
├── account/
│   ├── Account.java              (entity)
│   ├── AccountRepository.java    (Spring Data repo)
│   ├── AccountService.java       (business logic)
│   └── AccountController.java    (REST layer)
├── transfer/
├── topup/
├── ledger/                       (LedgerEntry, posting logic, reconciliation)
├── idempotency/
├── outbox/
└── common/                       (exception, config, web error handling)
```

**Pros:**

- Điều hướng tự nhiên: "xem logic chuyển khoản" → mở `transfer/` package.
- Module boundary visible: khi `TransferService` cần đọc account balance, phải import từ `account/` package — coupling rõ ràng, có thể enforce bằng ArchUnit nếu cần (LDG-17).
- Phase 2 service split rõ ràng: `account/` package → Account Service; `transfer/` package → Transfer Service. Chỉ cần tách ra, thêm HTTP client thay cho direct method call.
- DDD-aligned: mỗi package là một bounded context với aggregate root riêng (`Account`, `Transfer`).
- Phổ biến trong Spring Boot projects hiện đại.

**Cons:**

- Nếu một feature có nhiều layer phức tạp (ví dụ: transfer có cả saga, compensation, retry logic), package có thể phình to và cần sub-package.
- Không có enforcement tự động về việc `common/` không được import từ `transfer/` — chỉ là convention. (Có thể enforce bằng ArchUnit test nếu cần — LDG-17.)
- Với codebase nhỏ (Phase 0-1 ~50 class), có thể overkill — nhưng đây là đầu tư chuẩn bị cho Phase 2.

### Option B: Package-by-layer (layer-by-type)

Tổ chức theo technical role:

```
com.xidoke.ledger
├── controller/
├── service/
├── repository/
├── model/
└── config/
```

**Pros:**

- Quen thuộc với developer có background MVC truyền thống.
- Dễ hiểu ngay lập tức với người mới vào project — "service ở `service/`, controller ở `controller/`."
- Phổ biến trong introductory Spring Boot material.

**Cons:**

- **Driver 1 hoàn toàn bị miss**: không có ranh giới module nào cả. `AccountController` và `TransferController` nằm cạnh nhau trong `controller/`, `AccountService` và `TransferService` trong `service/` — không có cách nào biết cái nào thuộc domain nào mà không đọc code.
- Tách service ở Phase 2 trở nên đau: phải xác định từng class thuộc về service nào rồi di chuyển, sửa imports — không có ranh giới rõ để follow.
- Coupling ẩn: `TransferService` có thể import `AccountRepository` trực tiếp mà không có signal rõ ràng rằng đây là cross-boundary access.
- Không scale theo complexity: khi codebase lớn, mỗi layer trở thành "junk drawer" — tất cả classes của mọi feature đổ vào cùng một thư mục.
- Không phản ánh domain understanding — tech-centric thay vì domain-centric.

### Option C: Hexagonal Architecture (Ports and Adapters)

Tổ chức theo hexagonal / clean architecture:

```
com.xidoke.ledger
├── account/
│   ├── domain/        (pure domain object + port/interface)
│   ├── application/   (use case)
│   └── infrastructure/  (adapter: JPA repo, REST controller)
```

**Pros:**

- Domain logic thuần túy, không phụ thuộc framework.
- Unit test domain logic dễ — không cần Spring context.
- Separation of concerns rõ ràng: domain, application, infrastructure tách hoàn toàn.

**Cons:**

- **Overhead quá lớn cho Phase 0-1**: với ~50 class, hexagonal architecture tạo ra 3x số files và layers trừu tượng không cần thiết ở scale này.
- Spring Data JPA repository đã là port/adapter pattern ở mức nào đó — thêm layer interface cho repository là duplication.
- Port/Adapter enforcement cần discipline cao — không có tự động kiểm tra nếu không có ArchUnit tests.
- **Chọn nhầm mức phức tạp**: Hexagonal shine ở domain phức tạp với nhiều adapter (vừa HTTP REST vừa gRPC vừa batch). Ledger Service Phase 0-1 chỉ có HTTP adapter.

## Decision outcome

**Chọn Option A: Package-by-feature.**

Driver 4 (chuẩn bị cho Phase 2 service split) là driver quyết định: package-by-layer (Option B) làm cho Phase 2 đắt hơn đáng kể vì không có ranh giới domain nào để theo. Hexagonal (Option C) mang lợi ích nhỏ hơn chi phí boilerplate ở scale Phase 0-1.

Package-by-feature đạt được balance đúng: module boundaries đủ rõ để Phase 2 tách được sạch, đủ đơn giản để maintain, và phù hợp với DDD thinking về bounded contexts.

Cấu trúc cụ thể được chọn:

- `account/` — Account entity, AccountRepository, AccountService, AccountController
- `transfer/` — TransferService, TransferController (Transfer là operation, không phải entity có lifecycle riêng)
- `topup/` — TopupService, TopupController
- `ledger/` — LedgerEntry entity (append-only), LedgerEntryRepository, LedgerPostingService (double-entry posting logic, reconciliation)
- `idempotency/` — IdempotencyKey entity, IdempotencyService
- `outbox/` — OutboxEvent entity, OutboxRepository, OutboxPoller (Spring Scheduler)
- `common/` — exception handling, Spring config, MDC logging setup, global error handler

## Consequences

**Positive:**

- Phase 2 service split có path rõ ràng: mỗi package → một service candidate. Cross-package dependencies đánh dấu các HTTP calls cần tạo.
- Developer mới điều hướng dễ hơn — tìm theo feature, không theo layer.
- IDE import suggestions trở nên có ý nghĩa: nếu `TransferService` cần import từ `ledger/`, đó là signal coupling cần review.
- Có thể thêm ArchUnit test (LDG-17) để enforce "module `transfer` không được import trực tiếp `AccountJpaRepository`" — chỉ allowed import qua `AccountService` interface.

**Negative:**

- Không có enforcement tự động mà không có ArchUnit — convention dễ vỡ khi vội vàng.
- Package `ledger/` có thể gây nhầm lẫn (tên giống tên project) — đây là package cho `LedgerEntry` posting logic cụ thể, không phải toàn bộ domain.
- Với codebase nhỏ ban đầu, nhiều package chỉ có 3-4 files — có thể cảm thấy over-engineered.

**Neutral:**

- Flyway migration files không ảnh hưởng bởi package structure — vẫn ở `resources/db/migration/`.
- Spring Boot component scan tự động phát hiện `@Component`, `@Service`, `@Repository` trong mọi subpackage — không cần cấu hình thêm.
- Test files mirror cùng package structure trong `src/test/java/`.

## Risks & open questions

**Rủi ro 1: Cross-module coupling ẩn.** `TransferService` cần kiểm tra balance của account trước khi tạo transfer — đây là cross-module call hợp lệ, nhưng phải qua `AccountService` (service layer), không phải `AccountRepository` trực tiếp. Convention này dễ bị vi phạm dưới time pressure. Mitigation: code review checklist, hoặc ArchUnit test đơn giản.

**Rủi ro 2: `common/` trở thành junk drawer.** Package `common/` dễ trở thành nơi đổ tất cả những gì không biết để đâu. Mitigation: chỉ để `common/` chứa cross-cutting concerns thực sự (exception hierarchy, Spring config, MDC setup, error response format) — không để business logic ở đây.

**Câu hỏi mở:**

- Khi Transfer cần tạo LedgerEntry, ai là người gọi ai? `TransferService` gọi `LedgerPostingService`, hay `LedgerPostingService` là orchestrator? (Thiên về: `TransferService` orchestrate, `LedgerPostingService` là utility được inject — Transfer là domain operation cấp cao, Ledger Posting là primitive cấp thấp.)
- Nếu Phase 1 thêm auth, `security/` package vào `common/` hay package riêng?

## References

- [ADR-0001](0001-architectural-style.md) — modular monolith với module boundaries rõ ràng cho Phase 2 service split
- [ADR-0002](0002-language-framework.md) — Spring Boot component scan hoạt động tự nhiên với package-by-feature
- [ADR-0005](0005-ledger-model.md) — LedgerEntry là entity trong `ledger/` package — bounded context riêng với Account
- Source analysis: `Research/ledger-system-ADR/wiki/adr-004-package-structure.md`
- Vault wikis referenced: `changing-aggregate-boundaries`, `distributed-transactions-for-money`
