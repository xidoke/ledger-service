# syntax=docker/dockerfile:1

# ── Stage 1: build ──────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Maven wrapper + POM first — these change rarely, so dependency resolution
# stays cached across source-only changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B

# Source changes often — copy after deps to keep the cache hit above.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests -B && \
    cp target/*.jar application.jar && \
    java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ── Stage 2: runtime ────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# curl is used by the HEALTHCHECK below (not present in the JRE base image).
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN groupadd -r appuser && \
    useradd -r -g appuser --uid 10001 -d /app -s /usr/sbin/nologin appuser

WORKDIR /app

# Copy layered-jar pieces least- to most-frequently changed for independent
# layer caching (deps rarely change; application/ changes every build).
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# `jarmode=tools extract` produces a launcher jar (application.jar) that
# references the extracted dependencies/ layer — run it with -jar.
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+UseG1GC", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "application.jar"]
