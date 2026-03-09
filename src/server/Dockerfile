# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY contracts contracts
COPY server server
COPY webApp webApp

RUN mkdir -p shared androidApp desktopApp

RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /workspace/server/build/install/server /app

RUN groupadd --system linkstash \
    && useradd --system --gid linkstash --home-dir /app --create-home linkstash \
    && mkdir -p /data \
    && chown -R linkstash:linkstash /app /data

ENV HOST=0.0.0.0 \
    PORT=8080 \
    DB_URL=jdbc:sqlite:/data/linkstash.db

EXPOSE 8080

VOLUME ["/data"]

USER linkstash

ENTRYPOINT ["/app/bin/server"]
