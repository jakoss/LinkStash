# Phase 5 — Domain Endpoints (Spaces + Links)

## Goal
Expose a small LinkStash domain API (not a raw Raindrop proxy):
- Spaces CRUD (scoped under LinkStash root)
- Links CRUD + move (scoped under LinkStash root subtree)
- Delete semantics map to Raindrop Trash

## Scope
In scope:
- Implement all v1 endpoints in `MIGRATION.md`
- Validate that `spaceId` belongs to LinkStash root before using it
- Consistent error envelope

Out of scope:
- Caching/ETags/rate-limit optimizations (later)

## Tasks (checklist)
- [x] Spaces:
  - [x] `GET /v1/spaces`
  - [x] `POST /v1/spaces`
  - [x] `PATCH /v1/spaces/{spaceId}`
  - [x] `DELETE /v1/spaces/{spaceId}`
- [x] Links:
  - [x] `GET /v1/spaces/{spaceId}/links` (newest-first + pagination)
  - [x] `POST /v1/spaces/{spaceId}/links`
  - [x] `PATCH /v1/links/{linkId}` (move)
  - [x] `DELETE /v1/links/{linkId}`
- [x] Guardrails:
  - [x] Verify space is under LinkStash root before listing/creating
  - [x] Verify link belongs to LinkStash root subtree before moving/deleting

## Deliverables
- End-to-end domain flow works against Raindrop:
  - create space → add link → move link → delete link → delete space

## Acceptance criteria
- API never touches non-LinkStash collections for a user.
- Error responses are stable and parseable by clients.

## Validation Notes
- 2026-02-21: Implementation compiles with `./gradlew :contracts:compileKotlinMetadata :server:compileKotlin`.
- Pending: run a live Raindrop flow to fully validate acceptance end-to-end (create space → add link → move link → delete link → delete space).
