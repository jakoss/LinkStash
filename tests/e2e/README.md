# LinkStash E2E (Playwright + Bun)

This folder contains the E2E suite for the Ktor API and the Compose for Web client.

## Scope
- Bearer auth token exchange and logout
- Cookie session login and session restore in the web app
- Space create, rename, and delete flows
- Link save, move, export, and delete flows
- End-to-end browser coverage against the real backend only

## Prerequisites
1. In `tests/e2e`, create `.env` from `.env.example` and set `E2E_RAINDROP_TOKEN`.
2. Install dependencies:
   - `bun install`
3. Install Playwright browsers unless you plan to use a locally installed browser channel:
   - `bun run install:browsers`
4. Optional: use installed Chrome instead of the bundled Chromium:
   - `E2E_BROWSER_CHANNEL=chrome`

## Environment
Playwright starts both local services automatically:
- `./scripts/start-api.sh`
- `./scripts/serve-web.sh`

Default URLs:
- API: `http://127.0.0.1:8080`
- Web: `http://127.0.0.1:8081`

Override them with:
- `E2E_API_BASE_URL`
- `E2E_WEB_BASE_URL`

The API startup script also sets local defaults for:
- SQLite test DB
- session/token encryption secrets
- `CORS_ALLOWED_ORIGINS` for the Compose web origin

## Run
API auth only:
```bash
cd tests/e2e
bun run test:auth
```

Compose web only:
```bash
cd tests/e2e
bun run test:web
```

Full suite:
```bash
cd tests/e2e
bun run test
```

Headed mode:
```bash
cd tests/e2e
bun run test:headed
```
