# Convex → Ktor + Raindrop Pivot (Migration Plan)

Last updated: 2026-02-08

## Goal
Replace the Convex backend (and Bun/TS web app) with a small Ktor API that uses **Raindrop.io** as the system of record:
- **Spaces** = Raindrop **collections** nested under a dedicated “LinkStash root” collection
- **Links** = Raindrop “raindrops” (bookmarks) within those collections
- **Archive** = dropped; “mark as read” becomes **delete** (moves to Raindrop “Trash”)

Key constraints:
- Clients **never** call Raindrop APIs directly (except redirecting to Raindrop OAuth in the browser).
- Backend stores Raindrop tokens and manages refresh.
- Backend issues its **own** session tokens/cookies for client auth.
- Contracts are shared across backend/android/web via Kotlin Multiplatform.

Non-goals for the first iteration:
- Caching, ETags, or aggressive rate-limit optimizations (do later).
- Full data migration from Convex → Raindrop (assume minimal/no production data).
- Advanced search, tagging, notes, etc.

## Phase 0 decisions (locked on 2026-02-08)
### Environment configuration
- `LINKSTASH_ROOT_COLLECTION_TITLE=LinkStash`
- `LINKSTASH_DEFAULT_SPACE_TITLE=Inbox`
- Local development base URLs:
  - API: `http://localhost:8080`
  - Web: `http://localhost:8081`
- Production base URLs:
  - API: `https://api.linkstash.app`
  - Web: `https://linkstash.app`

### Raindrop OAuth app setup
- Register one Raindrop OAuth app for LinkStash and keep credentials only in environment variables:
  - `RAINDROP_CLIENT_ID`
  - `RAINDROP_CLIENT_SECRET`
- Allowed redirect URIs:
  - Web local/dev callback: `http://localhost:8080/v1/auth/raindrop/callback`
  - Web production callback: `https://api.linkstash.app/v1/auth/raindrop/callback`
  - Mobile custom-scheme callback: `linkstash://auth/callback`
  - Mobile app-link callback (fallback): `https://linkstash.app/auth/callback`

### PKCE policy
- PKCE is required for mobile and recommended for web; LinkStash backend stores verifier data in `oauth_states` for the exchange flow.

## Target architecture (high-level)
- **Ktor API**: domain endpoints (spaces/links) + auth + Raindrop integration.
- **DB (SQLite)**: users, sessions, Raindrop tokens, LinkStash root/default collection ids, and minimal OAuth/auth state.
- **Contracts module (KMP)**: request/response contracts + (optional) API client helpers.
- **Android client**: share-to-save + browsing + move/delete.
- **Web client (Compose for Web)**: minimal UI for paste-to-save + browsing + move/delete.

## Raindrop mapping (domain model)
### Collections / “Spaces”
We create (or reuse) a dedicated top-level Raindrop collection, e.g.:
- `LINKSTASH_ROOT_COLLECTION_TITLE=LinkStash` (configurable)
- `LINKSTASH_DEFAULT_SPACE_TITLE=Inbox` (configurable)

All LinkStash “spaces” are Raindrop collections with:
- `parent.$id = linkstashRootCollectionId`

Default space:
- Create a child collection under LinkStash root, e.g. `Inbox`, and treat it as the default space for new links.

### Links
Each LinkStash link maps to a Raindrop “raindrop” with:
- `link` = URL
- `collection.$id` = space collection id
- `pleaseParse={}` when creating (to let Raindrop fetch metadata asynchronously)

### Delete (no archive)
Deleting a link calls Raindrop delete:
- `DELETE /rest/v1/raindrop/{id}` (moves item to Raindrop “Trash”; deleting again from Trash is permanent)

Deleting a space:
- `DELETE /rest/v1/collection/{id}` (removes collection and descendants; raindrops move to Trash)

## Authentication & session model
### Raindrop OAuth (server-side token storage)
OAuth steps:
1. Client requests an auth start URL from our backend (backend generates `state`, optional PKCE, persists ephemeral state).
2. User authorizes the Raindrop app in a browser.
3. Client receives the `code` (either via backend callback redirect to web app, or via mobile deep link) and exchanges it with our backend.
4. Backend exchanges `code` for `access_token` + `refresh_token`, stores tokens, and returns a LinkStash session.

Refresh:
- Raindrop access tokens expire (~2 weeks); backend refreshes with `grant_type=refresh_token` as needed.

### LinkStash session
Backend issues its own session:
- Web: HTTP-only cookie (SameSite=Lax, Secure in prod).
- Mobile: bearer access token (no cookie jar).

CSRF:
- If using cookie sessions for web, require CSRF protection for write endpoints (double-submit token or per-session token).

Logout:
- Revoke LinkStash session server-side.
- Optionally revoke Raindrop tokens (or mark disconnected) and require re-auth.

## API surface (v1)
Expose a **LinkStash domain API**, not a raw Raindrop proxy. Suggested endpoints:

### Auth
- `POST /v1/auth/raindrop/start` → `{ url }`
- `POST /v1/auth/raindrop/exchange` → sets cookie / returns `{ accessToken, ... }` (LinkStash token)
- `POST /v1/auth/logout`
- `GET /v1/me` → current user + selected root/default ids

### Spaces
- `GET /v1/spaces` → list spaces (only under LinkStash root)
- `POST /v1/spaces` → create
- `PATCH /v1/spaces/{spaceId}` → rename
- `DELETE /v1/spaces/{spaceId}` → delete (moves items to Trash)

### Links
- `GET /v1/spaces/{spaceId}/links?cursor=...` → list newest-first
- `POST /v1/spaces/{spaceId}/links` → add link `{ url }`
- `PATCH /v1/links/{linkId}` → move to another space `{ spaceId }`
- `DELETE /v1/links/{linkId}` → delete (moves to Trash)

Error model:
- Standard JSON error envelope with stable `code` (e.g. `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `UPSTREAM_ERROR`) and optional `details`.

Pagination:
- Cursor-based (preferred) or page/perpage aligned with Raindrop. Implement minimal paging now; optimize later.

## Storage plan (DB schema)
Start minimal; keep it evolvable via migrations.

DB choice:
- SQLite for both local development and production (v1).

Tables (suggested):
- `users`: `id`, `createdAt`, `raindropUserId` (from `/user`), optional email/name.
- `raindrop_tokens`: `userId`, `accessTokenEnc`, `refreshTokenEnc`, `expiresAt`, `updatedAt`.
- `sessions`: `id`, `userId`, `tokenHash`, `createdAt`, `lastSeenAt`, `expiresAt`, `deviceInfo`.
- `linkstash_config`: `userId`, `rootCollectionId`, `defaultSpaceCollectionId`.
- `oauth_states`: `state`, `userAgentHash?`, `pkceVerifierEnc?`, `createdAt`, `expiresAt` (TTL).

Note:
- Persist Raindrop collection ids for LinkStash root/default to avoid repeated “find by title” calls; recreate and update if they become invalid.

Token encryption at rest:
- Encrypt access/refresh tokens with an app secret (e.g. AES-GCM) before persisting.

## Repo/module structure
The repository uses a single top-level KMP Gradle build under `src/`, and that build is extended over time to include server + web.

Target end state (single Gradle project under `src/`):
- `src/` (Gradle root: `settings.gradle.kts`, wrapper, version catalog)
  - `src/contracts/` (KMP: API contracts + optional typed API client helpers)
  - `src/shared/` (existing KMP shared UI module; keep as-is)
  - `src/androidApp/` (Android app)
  - `src/webApp/` (Compose for Web app)
  - `src/server/` (Ktor server, JVM)
  - `src/desktopApp/` (optional; current repo has it)

## Migration phases (do in order)
### Phase 0 — Product + repo alignment
- Update `PRD.md` to remove “archive toggle”; replace with “delete (moves to Raindrop Trash)”.
- Use `src/` as the single Gradle root project for Kotlin modules.
- Update `AGENTS.md` files to reflect the new stack (remove Convex references; add Ktor/Gradle commands; new module layout).
- Register Raindrop OAuth app; decide redirect URIs for:
  - Local dev web
  - Production web
  - Mobile (custom scheme / app link)

Acceptance:
- PRD reflects the new semantics.
- `AGENTS.md` files reflect the new stack.
- OAuth app created and secrets stored via env (not committed).

### Phase 1 — Contracts foundation (`contracts`)
- Create `contracts` module:
  - `Space`, `Link`, `User`, request/response DTOs
  - Stable error envelope
  - Minimal `ApiClient` wrapper (Ktor client) used by Android + web (optional in v1, but recommended)

Acceptance:
- Android and server can compile against the same DTOs.

### Phase 2 — Ktor API skeleton + DB
- Create Ktor server project with:
  - Config (env vars), structured logging, request IDs
  - DB connectivity + migrations
  - Health endpoint

Acceptance:
- Server starts locally, connects to DB, runs migrations.

### Phase 3 — Auth (Raindrop OAuth + LinkStash sessions)
- Implement:
  - `auth/start` (state + optional PKCE)
  - `auth/exchange` (code → tokens; create LinkStash session)
  - `me`, `logout`
  - Raindrop token refresh logic (refresh on expiry or 401)

Acceptance:
- User can sign in and obtain a LinkStash session; refresh works without re-auth.

### Phase 4 — Raindrop “LinkStash root” bootstrap
- On first authenticated request (or during exchange):
  - Fetch Raindrop user id (`GET /user`)
  - Ensure LinkStash root collection exists (create if missing) and persist `rootCollectionId` in `linkstash_config`
  - Ensure default space exists under that root (create if missing) and persist `defaultSpaceCollectionId` in `linkstash_config`
  - Use server-configured titles for initial create (and as a fallback if ids are missing), but treat ids as the primary selectors

Robustness:
- If stored ids are invalid (deleted), attempt rediscovery by configured titles; otherwise recreate and update `linkstash_config` (treat subtree as fresh/empty).

Acceptance:
- After login, `/spaces` returns at least the default space.

### Phase 5 — Domain endpoints (spaces + links)
- Implement endpoints using Raindrop collections/raindrops:
  - list/create/rename/delete spaces (scoped to root)
  - list/add/move/delete links (scoped to root subtree)
- Guardrails:
  - Never operate on collections outside the LinkStash subtree.
  - Validate `spaceId` belongs to LinkStash root before using it.

Acceptance:
- End-to-end: create space → add link → move link → delete link → delete space.

### Phase 6 — Android integration (share-to-save first)
- Replace Convex usage with LinkStash API client:
  - Auth flow (open browser, complete exchange)
  - Share intent → `POST /links`
  - Basic browse + move + delete
- Offline queue (per PRD):
  - Store pending URLs locally and flush when online (dedupe by URL is “nice-to-have” initially).

Acceptance:
- From Android Share menu: URL saved into Inbox in Raindrop within LinkStash root.

### Phase 7 — Web (Compose for Web)
- Build minimal Compose web UI:
  - Sign-in with Raindrop via backend
  - Paste-to-save
  - List spaces + links + move + delete
  - Export links in current space (newline-separated) client-side

Acceptance:
- Web supports the same core flow as Android (minus share intent).

### Phase 8 — Decommission Convex/Bun web
- Remove Convex backend code and dependencies once new stack is stable:
  - Delete/replace `web/` Bun+Convex app
  - Remove Convex environment/config docs
  - Update root `README.md` with new dev commands

Acceptance:
- No Convex dependency remains; docs point to Ktor + Compose web.

## Risks & mitigations
- **Raindrop API limits** (120 req/min/user): keep endpoints coarse; add caching later.
- **User deletes LinkStash root/default**: detect missing ids, rediscover by title or recreate, and update `linkstash_config` (cannot recover trashed items automatically).
- **Name collisions** (root title already exists): avoid binding to an unrelated existing collection purely by title; prefer creating a new dedicated root and persisting its id.
- **Trash semantics**: deleting from Trash is permanent; avoid ever deleting from Trash in LinkStash UX.
- **CSRF** (cookie sessions): enforce CSRF tokens for write endpoints if web uses cookies.

## Open decisions (confirm before implementation)
- Compose web target: Kotlin/JS vs Kotlin/Wasm (pick based on current Compose support and tooling).
