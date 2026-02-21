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
- [x] Auth:
  - [x] Open Raindrop auth URL in browser
  - [x] Handle redirect back into app (deep link)
  - [x] Call backend `auth/exchange`, store LinkStash bearer token
- [x] API integration:
  - [x] Use `contracts` DTOs + client helpers
  - [x] Ktor client engine: `io.ktor:ktor-client-okhttp`
- [x] Share-to-save:
  - [x] On share intent, enqueue URL locally and try to send immediately if online
- [x] Offline queue:
  - [x] Persist pending URLs locally in SQLite
  - [x] Retry on connectivity/app start
- [x] UI:
  - [x] List spaces
  - [x] List links in a space
  - [x] Move link to another space
  - [x] Delete link

## Deliverables
- Share a URL on Android → it appears in Raindrop under LinkStash Inbox.

## Acceptance criteria
- Share-to-save works reliably and remains fast.
- Offline URLs eventually sync when network is restored.

## Validation Notes
- 2026-02-21: Android implementation compiles with `./gradlew :androidApp:compileDebugKotlin` and installs on emulator via `./gradlew :androidApp:installDebug`.
- 2026-02-21: ADB validation confirmed app launch, login button flow opening browser, share-capable app intent wiring, and callback deep-link intent delivery to `MainActivity`.
- 2026-02-21: Offline/session storage migrated to Android Room + DataStore and LinkStash API/queue orchestration moved into `shared` module (`LinkStashRepository`, shared UI state/event models). Packaging validation passed with `./gradlew :androidApp:assembleDebug`.
- 2026-02-21: ViewModel business orchestration moved into shared `LinkStashController`; Android `LinkStashViewModel` now acts as a thin lifecycle + intent adapter. Validation passed with `./gradlew :shared:compileKotlinJvm :androidApp:compileDebugKotlin :androidApp:assembleDebug`.
- Pending: final manual emulator verification of authenticated in-app flow using your local browser/account state (login completion + share-save into Inbox + move/delete checks).
