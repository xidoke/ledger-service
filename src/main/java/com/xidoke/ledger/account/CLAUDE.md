# account package — CLAUDE.md

Owns the `Account` aggregate root and locking boundary (ADR-0010): cached `balance` + `version`, `status`, and the `debit`/`credit` domain methods that enforce the per-account invariants (account ACTIVE; no overdraw).

Structured hexagonally (ADR-0018):

- `domain/` — pure Java, framework-free (ArchUnit-enforced): `Account` aggregate, `AccountStatus`, domain exceptions, and the `AccountRepository` **port** (interface).
- `adapter/out/` — persistence adapter: `AccountJpaEntity` (Hibernate model, separate from the domain), `AccountJpaMapper`, and `AccountPersistenceAdapter implements AccountRepository` over Spring Data JPA.
- `adapter/in/` — REST controller (lands with the endpoints issue).

`Account` = JPA (CRUD + `@Version` optimistic lock); `LedgerEntry` append/query uses JdbcClient (later). Optimistic-lock retry is M4.

See repository root `CLAUDE.md` for project-wide conventions.
