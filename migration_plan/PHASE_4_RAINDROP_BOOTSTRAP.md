# Phase 4 — Raindrop “LinkStash root” Bootstrap

## Goal
Ensure all LinkStash operations are scoped to a dedicated Raindrop subtree:
- A dedicated LinkStash root collection
- A default space collection under that root
- Persist the ids in `linkstash_config` per user

## Scope
In scope:
- Bootstrap flow (run during auth exchange or first authenticated request)
- Robustness when ids become invalid (deleted)

Out of scope:
- Full domain endpoints (Phase 5)

## Strategy
- Primary selector: persisted ids (`linkstash_config`).
- Creation/fallback: server-configured titles:
  - `LINKSTASH_ROOT_COLLECTION_TITLE`
  - `LINKSTASH_DEFAULT_SPACE_TITLE`

## Tasks (checklist)
- [ ] Add `linkstash_config` row creation on first login:
  - [ ] Create root collection if missing, persist `rootCollectionId`
  - [ ] Create default space under root if missing, persist `defaultSpaceCollectionId`
- [ ] Add validation on each request:
  - [ ] If stored ids no longer exist, attempt rediscovery by title; otherwise recreate and update config
- [ ] Guardrail:
  - [ ] Never read/write collections outside `rootCollectionId`

## Deliverables
- `/v1/spaces` returns at least the default space after login.

## Acceptance criteria
- Bootstrap is idempotent and resilient to the user deleting the root/default collection.

