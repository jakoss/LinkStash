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
- [x] Add `src/contracts/` Gradle module and wire into `settings.gradle.kts`
- [x] Add serialization plugin + dependencies
- [x] Define DTOs and error envelope
- [x] Add minimal API client helpers (optional, but recommended):
  - [x] Base URL config
  - [x] Auth header injection (bearer)
  - [x] Error decoding into `ApiError`
  - [x] Ktor client engines per target:
    - [x] Android: `io.ktor:ktor-client-okhttp`
    - [x] JS (web): `io.ktor:ktor-client-js` (uses Fetch)

## Deliverables
- `src/contracts` module compiling for all required targets
- Types usable by server + clients

## Acceptance criteria
- Android + server can compile against `contracts` without any platform-specific code.
