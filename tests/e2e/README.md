# LinkStash E2E (Playwright + Bun)

This folder contains the E2E suite for LinkStash auth flow using Playwright.

## Scope
- Raindrop OAuth start (`/v1/auth/raindrop/start`)
- OAuth code exchange (`/v1/auth/raindrop/exchange`)
- Authenticated `GET /v1/me`
- Logout (`POST /v1/auth/logout`)
- Session revocation check (`GET /v1/me` returns `401` after logout)

## Prerequisites
1. Start API server from `src/`:
   - `./gradlew :server:runServerLocal`
2. Configure a Raindrop OAuth redirect URI matching your test env (default below).
3. In `tests/e2e`, create `.env` from `.env.example` and set credentials.
4. If you want to use installed Chrome (no Playwright browser download), set:
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
