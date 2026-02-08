# LinkStash Server (Phase 2 Skeleton)

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

Supported `local.properties` env formats:
- Direct env names, e.g. `DB_URL=jdbc:sqlite:/absolute/path/to/linkstash.db`
- Prefixed names, e.g. `server.env.DB_URL=jdbc:sqlite:/absolute/path/to/linkstash.db`

Server defaults:
- host: `0.0.0.0`
- port: `8080`
- health check: `GET /healthz`

Optional CORS override:
- `CORS_ALLOWED_ORIGINS` as comma-separated origins
  - example: `http://localhost:5173,http://127.0.0.1:5173`
