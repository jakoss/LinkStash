# LinkStash Server

## Run locally

From `src/`:

```bash
DB_URL="jdbc:sqlite:/absolute/path/to/linkstash.db" \
SESSION_SECRET="change-me" \
TOKEN_HASHING_SECRET="change-me-too" \
RAINDROP_TOKEN_ENCRYPTION_KEY="change-me-encryption-key" \
LINKSTASH_ROOT_COLLECTION_TITLE="LinkStash" \
LINKSTASH_DEFAULT_SPACE_TITLE="Inbox" \
./gradlew :server:run
```

Or use `local.properties`-driven run:

```bash
./gradlew :server:runServerLocal
```

`runServerLocal` now builds the wasm web UI and serves it from the same Ktor process.

Local URLs:
- app: `http://localhost:8080/`
- API: `http://localhost:8080/v1/...`

Supported `local.properties` env formats:
- Direct env names, e.g. `DB_URL=jdbc:sqlite:/absolute/path/to/linkstash.db`
- Prefixed names, e.g. `server.env.DB_URL=jdbc:sqlite:/absolute/path/to/linkstash.db`

Server defaults:
- host: `0.0.0.0`
- port: `8080`
- health check: `GET /healthz`

Optional CORS override:
- Same-origin deployments do not need special CORS configuration.
- Only set these when a different web origin must call the API.
- `CORS_ALLOWED_ORIGINS` as comma-separated origins
  - example: `https://linkstash.app,http://localhost:8081,http://127.0.0.1:8081`
- `CORS_ALLOWED_HEADERS` as comma-separated request headers
  - default includes `Authorization,Content-Type,X-Request-Id,X-CSRF-Token`
- `CORS_EXPOSED_HEADERS` as comma-separated response headers
  - default: `X-Request-Id`
- `CORS_ALLOW_CREDENTIALS=true|false`
- `CSRF_HEADER_NAME`
  - default: `X-CSRF-Token`

## Build a Docker image

From `src/`:

```bash
docker build -f server/Dockerfile -t linkstash-server:local .
```

The image exposes port `8080`, serves both the API and the wasm web UI, and defaults `DB_URL` to `jdbc:sqlite:/data/linkstash.db`.

Run it directly:

```bash
docker run --rm \
  -p 8080:8080 \
  -v linkstash-server-data:/data \
  -e SESSION_SECRET="change-me" \
  -e TOKEN_HASHING_SECRET="change-me-too" \
  -e RAINDROP_TOKEN_ENCRYPTION_KEY="change-me-encryption-key" \
  -e LINKSTASH_ROOT_COLLECTION_TITLE="LinkStash" \
  -e LINKSTASH_DEFAULT_SPACE_TITLE="Inbox" \
  linkstash-server:local
```

Then open:
- `http://localhost:8080/` for the app
- `http://localhost:8080/healthz` for the health check

## Install with Docker Compose

Docker Compose availability:
- Docker Desktop already includes Docker Compose.
- If you run Docker Engine directly on Linux, install the Docker Compose plugin and verify it with `docker compose version`.

From `src/`:

```bash
cp server/.env.compose.example server/.env.compose
```

Edit `server/.env.compose` and set:
- `LINKSTASH_SERVER_IMAGE` to the published GHCR tag you want to run
- `SESSION_SECRET`
- `TOKEN_HASHING_SECRET`
- `RAINDROP_TOKEN_ENCRYPTION_KEY`
- `CORS_ALLOWED_ORIGINS` only if a different web origin will call the API
  - same-origin deployment example: leave unset
  - separate frontend example: `https://linkstash.app`
  - local separate frontend example: `http://localhost:8081,http://127.0.0.1:8081`

Start the backend:

```bash
docker compose -f server/compose.yaml --env-file server/.env.compose up -d
```

Useful commands:

```bash
docker compose -f server/compose.yaml --env-file server/.env.compose logs -f
docker compose -f server/compose.yaml --env-file server/.env.compose down
```

After startup, open `http://localhost:${HOST_PORT:-8080}/`.

## API tests (Ktor test host + JUnit)

1. Create `src/server/.env.api-test` from `src/server/.env.api-test.example`.
2. Set `API_TEST_RAINDROP_TOKEN` (you can reuse the token from `tests/e2e/.env`).
3. Run from `src/`:

```bash
./gradlew :server:test
```

Optional: use a custom env file path:

```bash
./gradlew :server:test -PapiTestEnvFile=/absolute/path/to/.env
```

## Auth + domain endpoints

- `POST /v1/auth/raindrop/token` (expects `{ "accessToken": "...", "sessionMode": "BEARER" }`)
- `GET /v1/auth/csrf` (cookie-authenticated web clients use this to restore the CSRF token after reload)
- `GET /v1/me` (requires cookie or bearer auth)
- `POST /v1/auth/logout` (requires cookie or bearer auth)
- `GET /v1/spaces` (requires cookie or bearer auth; scoped under LinkStash root)
- `POST /v1/spaces`
- `PATCH /v1/spaces/{spaceId}`
- `DELETE /v1/spaces/{spaceId}`
- `GET /v1/spaces/{spaceId}/links?cursor=...`
- `POST /v1/spaces/{spaceId}/links`
- `PATCH /v1/links/{linkId}` (move with `{ \"spaceId\": \"...\" }`)
- `DELETE /v1/links/{linkId}`

Cookie-authenticated web writes require the CSRF header configured by `CSRF_HEADER_NAME` (default `X-CSRF-Token`).
