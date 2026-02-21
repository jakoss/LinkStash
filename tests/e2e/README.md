# LinkStash E2E (Playwright + Bun)

This folder contains the E2E suite for LinkStash token auth flow using Playwright.

## Scope
- Raindrop token exchange (`/v1/auth/raindrop/token`)
- Authenticated `GET /v1/me`
- Logout (`POST /v1/auth/logout`)
- Session revocation check (`GET /v1/me` returns `401` after logout)

## Prerequisites
1. Start API server from `src/`:
   - `./gradlew :server:runServerLocal`
2. In `tests/e2e`, create `.env` from `.env.example` and set a Raindrop API token.
3. If you want to use installed Chrome (no Playwright browser download), set:
   - `E2E_BROWSER_CHANNEL=chrome`

## Install
```bash
cd tests/e2e
bun install
bun run install:browsers
```

If using installed Chrome (`E2E_BROWSER_CHANNEL=chrome`), `bun run install:browsers` is optional.

## Run
```bash
cd tests/e2e
bun run test:auth
```

Run all E2E specs:
```bash
bun run test
```

Headed mode:
```bash
bun run test:headed
```
