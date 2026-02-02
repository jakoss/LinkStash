# Repository Guidelines

## Project Overview
This is the Bun + Convex web app for LinkStash. It provides the primary UI and backend functions for saving URLs, organizing them into spaces, and browsing archived links.

## Project Structure & Module Organization
- `index.ts` is the primary entry point for the app runtime.
- `convex/` contains Convex backend functions and generated bindings.
  - `convex/_generated/` is auto-generated; do not edit by hand.
- `package.json` defines Bun/TypeScript dependencies.
- `tsconfig.json` applies project-wide TypeScript compiler settings.

## Build, Test, and Development Commands
- `bun install` installs dependencies via Bun.
- `bun run index.ts` runs the main entry point locally.
- `npx convex -h` lists Convex CLI commands (useful for deployments).
- `npx convex docs` opens Convex documentation locally.
- `bun run test:e2e` runs Playwright end-to-end tests.

## Coding Style & Naming Conventions
- TypeScript is the primary language; follow strict compiler settings from `tsconfig.json`.
- Use modern ES module syntax and keep `type: module` compatibility in mind.
- Prefer descriptive, lowerCamelCase function and variable names.
- No formatter or linter is configured; keep code readable and consistent with existing style.

## Agent Skills
- Use the `convex` skill for any web development work that touches Convex or the app UI.
- Follow Convex guidelines: new function syntax, explicit argument/return validators, and index names that include all fields.

## Testing Guidelines
- Playwright is used for end-to-end tests; keep new UI flows covered with E2E tests.
- Run `bun run test:e2e` as part of the development lifecycle before shipping changes.
- When running E2E tests against the dev backend, set `CONVEX_URL=https://zealous-hawk-470.convex.cloud`.
- Convex MCP tools can be used to inspect or seed data in the dev deployment.
- E2E runs require `E2E_EMAIL` to be set so the auth flow can proceed.

## Development Workflow
- Develop locally: `bun install` then `bun run index.ts`.
- Add/update tests alongside features, keep `tests/` in sync with UI flows.
- If Convex functions change, deploy to dev with `bunx convex dev --once`.
- Run E2E tests with `CONVEX_URL=https://zealous-hawk-470.convex.cloud bun run test:e2e`.

## Authentication Setup
- Convex Auth is used for web with email magic links (Resend provider).
- Required Convex env vars: `SITE_URL`, `CONVEX_SITE_URL`, `JWT_PRIVATE_KEY`, `JWKS`, `AUTH_RESEND_KEY`, `AUTH_EMAIL_FROM`.
- `CONVEX_URL` must be set for the frontend to connect to Convex.
- Session policy: 30-day rolling inactivity window; total duration set to ~10 years.

## Configuration & Safety Notes
- Generated Convex files live under `convex/_generated/`; treat them as build artifacts.
- Keep secrets out of the repo; use environment variables or Convex config as needed.
