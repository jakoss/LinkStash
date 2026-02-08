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
- [ ] Spaces:
  - [ ] `GET /v1/spaces`
  - [ ] `POST /v1/spaces`
  - [ ] `PATCH /v1/spaces/{spaceId}`
  - [ ] `DELETE /v1/spaces/{spaceId}`
- [ ] Links:
  - [ ] `GET /v1/spaces/{spaceId}/links` (newest-first + pagination)
  - [ ] `POST /v1/spaces/{spaceId}/links`
  - [ ] `PATCH /v1/links/{linkId}` (move)
  - [ ] `DELETE /v1/links/{linkId}`
- [ ] Guardrails:
  - [ ] Verify space is under LinkStash root before listing/creating
  - [ ] Verify link belongs to LinkStash root subtree before moving/deleting

## Deliverables
- End-to-end domain flow works against Raindrop:
  - create space → add link → move link → delete link → delete space

## Acceptance criteria
- API never touches non-LinkStash collections for a user.
- Error responses are stable and parseable by clients.

