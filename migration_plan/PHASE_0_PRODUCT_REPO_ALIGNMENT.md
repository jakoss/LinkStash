# Phase 0 — Product + Repo Alignment

## Goal
Align product docs + repository structure with the new stack:
- Ktor backend + Raindrop.io as storage/auth
- Kotlin Multiplatform contracts module
- Compose for Web frontend
- Android share-to-save client

## Scope
In scope:
- Update product requirements to reflect **delete instead of archive**
- Adopt a **single Gradle root** by renaming `mobile/` → `src/`
- Update `AGENTS.md` files to remove Convex guidance and add new commands/structure
- Register/configure Raindrop OAuth app and define redirect/deep link strategy

Out of scope:
- Any implementation of server endpoints or UI (phases 1+)

## Tasks (checklist)
- [x] Update `PRD.md`:
  - [x] Remove “archive toggle” feature and replace with “delete (moves to Raindrop Trash)”
  - [x] Update any flows/requirements mentioning archived lists
- [x] Decide/record environment configuration:
  - [x] `LINKSTASH_ROOT_COLLECTION_TITLE`
  - [x] `LINKSTASH_DEFAULT_SPACE_TITLE`
  - [x] Base URLs for local and prod (API + web)
- [ ] Raindrop OAuth setup:
  - [ ] Create Raindrop app and obtain `client_id` / `client_secret` (manual external action)
  - [x] Decide redirect URIs:
    - [x] Web local/dev
    - [x] Web prod
    - [x] Mobile deep link (custom scheme and app link fallback)
  - [x] Decide whether to use PKCE (required for mobile, recommended for web)
- [x] Repo restructure (no behavior changes, just layout):
  - [x] Rename `mobile/` → `src/`
  - [x] Ensure the Gradle wrapper remains under `src/`
  - [x] Update any root docs that reference `mobile/` paths
- [x] Update `AGENTS.md` files:
  - [x] Root `AGENTS.md`: update the “Project Overview” to reflect Raindrop + Ktor
  - [x] Former `web/AGENTS.md`: remove Convex-specific instructions; add Compose web guidance
  - [x] `src/AGENTS.md` (formerly `mobile/AGENTS.md`): keep KMP notes; add `contracts`/`server` modules

## Deliverables
- Updated docs (`PRD.md`, relevant `AGENTS.md`)
- A defined OAuth/deep-link strategy documented in `MIGRATION.md`
- Repository ready for Phase 1 module creation

## Acceptance criteria
- Docs no longer mention Convex as the backend plan.
- “Archive” is removed from the v1 scope; delete semantics are clear.
- The repo has a clear “single Gradle root under `src/`” plan reflected in docs.
