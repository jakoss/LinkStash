# Phase 1 — Contracts Foundation (`contracts`)

## Goal
Introduce a new `contracts` Kotlin Multiplatform module to share:
- Request/response DTOs (API shapes)
- A stable error envelope
- Optional typed API client helpers (recommended)

This module is shared by:
- Ktor server
- Android app
- Web app (Compose for Web)

## Scope
In scope:
- Create `src/contracts` module
- Define DTOs for spaces/links/auth
- Define error model and standard codes
- Ensure serialization is consistent (`kotlinx.serialization`)

Out of scope:
- Any real server logic, persistence, or Raindrop integration

## Proposed package layout
- `pl.jsyty.linkstash.contracts.*` (or similar top-level namespace)

## Data model (v1)
DTOs to define (suggested):
- `UserDto` (minimal: id, displayName?)
- `SpaceDto` (id, title, createdAt?)
- `LinkDto` (id, url, title?, excerpt?, createdAt?, spaceId)

Requests/responses:
- `AuthStartResponse` (`url`)
- `AuthExchangeRequest` (`code`, `state`, `redirectUri`, optional `codeVerifier`)
- `SpacesListResponse`
- `SpaceCreateRequest`, `SpaceRenameRequest`
- `LinksListResponse` (include cursor/paging)
- `LinkCreateRequest` (`url`)
- `LinkMoveRequest` (`spaceId`)

Error envelope:
- `ApiError` with fields like:
  - `code` (enum/string)
  - `message` (safe for UI)
  - `details` (optional structured map)

## Tasks (checklist)
- [ ] Add `src/contracts/` Gradle module and wire into `settings.gradle.kts`
- [ ] Add serialization plugin + dependencies
- [ ] Define DTOs and error envelope
- [ ] Add minimal API client helpers (optional, but recommended):
  - [ ] Base URL config
  - [ ] Auth header injection (bearer)
  - [ ] Error decoding into `ApiError`
  - [ ] Ktor client engines per target:
    - [ ] Android: `io.ktor:ktor-client-okhttp`
    - [ ] JS (web): `io.ktor:ktor-client-js` (uses Fetch)

## Deliverables
- `src/contracts` module compiling for all required targets
- Types usable by server + clients

## Acceptance criteria
- Android + server can compile against `contracts` without any platform-specific code.
