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
- SQLite persistence via **JetBrains Exposed** using the **JDBC** stack (schema + migrations handled in Exposed)
- Health endpoint and basic middleware (logging, request id)

Out of scope:
- Raindrop OAuth implementation (Phase 3)
- Domain endpoints (Phase 5)

## Tasks (checklist)
- [x] Create `src/server` module and basic Ktor app:
  - [x] JSON serialization
  - [x] CORS configured for local/dev web (and prod origins later)
  - [x] Request logging
- [x] Add DB layer:
  - [x] Use **Exposed** for all DB work (schema, queries, migrations): https://www.jetbrains.com/help/exposed/working-with-database.html#sqlite
  - [x] Use SQLite + JDBC in implementation:
    - [x] Exposed JDBC module (`exposed-jdbc`)
    - [x] SQLite JDBC driver (`org.xerial:sqlite-jdbc:3.51.1.0`)
    - [x] Configure connection via `DB_URL` (`jdbc:sqlite:/absolute/path/to/linkstash.db`)
  - [x] Schema:
    - [x] Define tables in Exposed (`users`, `raindrop_tokens`, `sessions`, `linkstash_config`, `oauth_states`)
  - [x] Migrations (Exposed-driven):
    - [x] Implement a minimal `schema_migrations` table + code-based migration list
    - [x] For v1 bootstrap, run an idempotent “ensure schema” step (create missing tables/columns) on startup
- [x] Add `GET /healthz` endpoint
- [x] Add basic config via env:
  - [x] `DB_URL` (JDBC SQLite URL)
  - [x] Session secret / token hashing secret
  - [x] Encryption key for Raindrop tokens-at-rest
  - [x] `LINKSTASH_ROOT_COLLECTION_TITLE`, `LINKSTASH_DEFAULT_SPACE_TITLE`

## Deliverables
- Server starts locally and initializes DB schema.
- Documented run command(s) in `AGENTS.md` / README.

## Acceptance criteria
- `GET /healthz` returns 200.
- Migrations run cleanly on a fresh database.

## Implementation Notes
- Updated per review request: runtime DB driver uses SQLite JDBC (`org.xerial:sqlite-jdbc:3.51.1.0`).
