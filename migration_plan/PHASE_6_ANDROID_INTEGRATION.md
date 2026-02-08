# Phase 6 — Android Integration (Share-to-Save First)

## Goal
Replace Convex usage with the new LinkStash API:
- OAuth login through Raindrop (browser) + backend exchange
- Share intent saves into default space
- Basic browse + move + delete
- Offline queue for share-to-save (PRD requirement)

## Scope
In scope:
- Auth flow on Android
- Share-to-save MVP
- Basic spaces/links UI flows
- Offline queue and sync (minimal)

Out of scope:
- Perfect dedupe; keep it simple initially

## Tasks (checklist)
- [ ] Auth:
  - [ ] Open Raindrop auth URL in browser
  - [ ] Handle redirect back into app (deep link)
  - [ ] Call backend `auth/exchange`, store LinkStash bearer token
- [ ] API integration:
  - [ ] Use `contracts` DTOs + client helpers
  - [ ] Ktor client engine: `io.ktor:ktor-client-okhttp`
- [ ] Share-to-save:
  - [ ] On share intent, enqueue URL locally and try to send immediately if online
- [ ] Offline queue:
  - [ ] Persist pending URLs locally in SQLite
  - [ ] Retry on connectivity/app start
- [ ] UI:
  - [ ] List spaces
  - [ ] List links in a space
  - [ ] Move link to another space
  - [ ] Delete link

## Deliverables
- Share a URL on Android → it appears in Raindrop under LinkStash Inbox.

## Acceptance criteria
- Share-to-save works reliably and remains fast.
- Offline URLs eventually sync when network is restored.
