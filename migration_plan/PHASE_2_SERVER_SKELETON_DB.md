# Phase 2 — Ktor API Skeleton + SQLite

## Goal
Stand up the server skeleton and persistence foundation (SQLite) so later phases can plug in:
- Raindrop OAuth exchange and token storage
- LinkStash sessions
- Domain endpoints

## Scope
In scope:
- Create `src/server` module (Ktor, JVM)
- Environment-based config
- SQLite persistence via **JetBrains Exposed** using the **R2DBC** stack (schema + migrations handled in Exposed)
- Health endpoint and basic middleware (logging, request id)

Out of scope:
- Raindrop OAuth implementation (Phase 3)
- Domain endpoints (Phase 5)

## Tasks (checklist)
- [ ] Create `src/server` module and basic Ktor app:
  - [ ] JSON serialization
  - [ ] CORS configured for local/dev web (and prod origins later)
  - [ ] Request logging
- [ ] Add DB layer:
  - [ ] Use **Exposed** for all DB work (schema, queries, migrations): https://www.jetbrains.com/help/exposed/working-with-database.html#sqlite
  - [ ] Use **R2DBC**, not JDBC:
    - [ ] Exposed R2DBC module (`exposed-r2dbc`)
    - [ ] SQLite R2DBC driver (`io.r2dbc:r2dbc-sqlite`)
    - [ ] Configure connection via `DB_URL` like `r2dbc:sqlite:///absolute/path/to/linkstash.db`
  - [ ] Schema:
    - [ ] Define tables in Exposed (`users`, `raindrop_tokens`, `sessions`, `linkstash_config`, `oauth_states`)
  - [ ] Migrations (Exposed-driven):
    - [ ] Implement a minimal `schema_migrations` table + code-based migration list
    - [ ] For v1 bootstrap, run an idempotent “ensure schema” step (create missing tables/columns) on startup
- [ ] Add `GET /healthz` endpoint
- [ ] Add basic config via env:
  - [ ] `DB_URL` (R2DBC SQLite URL)
  - [ ] Session secret / token hashing secret
  - [ ] Encryption key for Raindrop tokens-at-rest
  - [ ] `LINKSTASH_ROOT_COLLECTION_TITLE`, `LINKSTASH_DEFAULT_SPACE_TITLE`

## Deliverables
- Server starts locally and initializes DB schema.
- Documented run command(s) in `AGENTS.md` / README.

## Acceptance criteria
- `GET /healthz` returns 200.
- Migrations run cleanly on a fresh database.
