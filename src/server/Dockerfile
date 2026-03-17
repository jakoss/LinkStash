# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk@sha256:c3b86e3867140345626eba1927a29e2df58c8619ec1c9b0951c0fdd7df97a145 AS builder

RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 nodejs npm \
    && npm install -g yarn@1.22.17 \
    && rm -rf /var/lib/apt/lists/*

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

FROM eclipse-temurin:25-jre@sha256:ff82c74a630c08b417502c4c18189e62074edbb28eb1f2d31b799ac138c01058

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
