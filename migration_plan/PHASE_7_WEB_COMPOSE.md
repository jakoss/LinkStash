# Phase 7 — Web (Compose for Web)

## Goal
Build the web client using Compose for Web:
- Sign in via backend with a manually pasted Raindrop token
- Paste-to-save
- Browse spaces and links
- Move and delete links
- Export links from current space (newline-separated)

## Scope
In scope:
- Minimal web UX that mirrors core flows
- Cookie-based session auth (HTTP-only cookie) + CSRF for writes

Out of scope:
- Advanced UI polish and caching

## Tasks (checklist)
- [ ] Auth:
  - [ ] Token input accepts a user-pasted Raindrop API token
  - [ ] Web app POSTs the token to backend `/v1/auth/raindrop/token` and receives an HTTP-only session cookie
- [ ] Core UI:
  - [ ] Paste URL to save into default/current space
  - [ ] List spaces and switch between them
  - [ ] List links (newest-first)
  - [ ] Move link
  - [ ] Delete link
  - [ ] Export current space links
- [ ] Networking:
  - [ ] Ktor client engine: `io.ktor:ktor-client-js` (Fetch)
- [ ] Security:
  - [ ] CSRF protection for write endpoints (if cookie sessions)

## Deliverables
- Working web app that can save and organize links in Raindrop via the Ktor backend.

## Acceptance criteria
- Web flow never calls Raindrop directly (only backend).
- CORS is handled by the backend.
