# 00 · Prerequisites

Install these once before anything else. The whole local stack runs through the
Maven wrapper and Docker — you do **not** need a local Maven or a local Postgres.

## Required

- **JDK 21** (LTS). [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21)
  is the reference build.

  ```bash
  java -version   # → openjdk version "21.x"
  ```

  If you juggle multiple JDKs, [SDKMAN!](https://sdkman.io/) makes switching easy:
  `sdk install java 21-tem && sdk use java 21-tem`.

- **Docker**, running. Docker Desktop, [Colima](https://github.com/abiosoft/colima),
  or [OrbStack](https://orbstack.dev/) all work. The app auto-starts a `postgres:17`
  container, and the integration tests use Testcontainers — both need a live Docker daemon.

  ```bash
  docker info > /dev/null && echo "docker ok"
  ```
- **Git**.

## Recommended

- **IntelliJ IDEA** (Community is enough). Enable *Palantir Java Format* via the
  Spotless workflow — the build formats for you, so IDE settings are optional.
- **lefthook** for the pre-commit hooks (Spotless + Conventional-Commits message check):

  ```bash
  brew install lefthook && lefthook install
  ```

  Without it the hooks just don't run locally; CI still enforces the same checks.

## You do NOT need

- A local Maven — always use `./mvnw` (the wrapper pins the version).
- A local PostgreSQL — Docker provides it.

Next: [01 · First run](01-first-run.md).
