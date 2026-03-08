# Phase 6 — Android Integration (Share-to-Save First)

## Goal
Replace Convex usage with the new LinkStash API:
- Manual Raindrop token login + backend exchange
- Share intent saves into default space
- Basic browse + move + delete
- Offline queue for share-to-save (PRD requirement)

## Scope
In scope:
- Manual token auth flow on Android
- Share-to-save MVP
- Basic spaces/links UI flows
- Offline queue and sync (minimal)

Out of scope:
- Perfect dedupe; keep it simple initially

## Tasks (checklist)
- [x] Auth:
  - [x] Accept a pasted Raindrop token
  - [x] Call backend `auth/raindrop/token`, store LinkStash bearer token
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
- 2026-02-21: ADB validation confirmed app launch, manual token entry UI, and share-capable app intent wiring to `MainActivity`.
- 2026-02-21: Offline/session storage migrated to Android Room + DataStore and LinkStash API/queue orchestration moved into `shared` module (`LinkStashRepository`, shared UI state/event models). Packaging validation passed with `./gradlew :androidApp:assembleDebug`.
- 2026-02-21: ViewModel business orchestration moved into shared `LinkStashController`; Android `LinkStashViewModel` now acts as a thin lifecycle + intent adapter. Validation passed with `./gradlew :shared:compileKotlinJvm :androidApp:compileDebugKotlin :androidApp:assembleDebug`.
- 2026-03-07: Share intent handling was broadened to accept `ACTION_SEND`/`ACTION_SEND_MULTIPLE` text payloads, prevent duplicate reprocessing after activity recreation, and normalize pasted/shared URLs before queueing. Emulator validation confirmed warm-start and cold-start shares enqueue correctly in Room, including URLs embedded in surrounding text and URLs with trailing punctuation.
- 2026-03-07: Android UI was refreshed to a Material 3 inbox-first layout. Inbox remains the default selected space, refresh/sync/logout moved into an overflow menu, and manual URL entry moved into a bottom sheet launched from the floating action button. Visual validation was completed on the Pixel 9 AVD with ADB screenshots.
- 2026-03-08: Final authenticated emulator validation completed using the local test token from `tests/e2e/.env`. Verified token login, Inbox default selection, share-save into Inbox, move to another space, and delete to trash against the local server and live Raindrop-backed account.
