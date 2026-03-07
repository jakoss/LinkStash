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
- Document the temporary manual Raindrop token auth strategy

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
- [x] Auth strategy:
  - [x] Decide that clients will temporarily require a manually pasted Raindrop API token
  - [x] Document deferred OAuth/PKCE work as follow-up, not current scope
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
- A defined manual-token auth strategy documented in `MIGRATION.md`
- Repository ready for Phase 1 module creation

## Acceptance criteria
- Docs no longer mention Convex as the backend plan.
- “Archive” is removed from the v1 scope; delete semantics are clear.
- The repo has a clear “single Gradle root under `src/`” plan reflected in docs.
