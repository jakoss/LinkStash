# Repository Guidelines

## Project Overview
`web/` contains the legacy Bun + TypeScript web app from the Convex era. During migration, treat this area as maintenance-only while new web product work moves to `src/webApp` (Compose for Web).

## Project Structure & Module Organization
- `index.ts` is the legacy runtime entry point.
- `public/` contains the existing frontend.
- `tests/` contains Playwright end-to-end tests for legacy flows.
- `convex/_generated/` is generated code for the legacy backend and should not be edited by hand.

## Build, Test, and Development Commands
- `bun install` installs dependencies via Bun.
- `bun run index.ts` runs the legacy app locally.
- `bun run test:e2e` runs Playwright end-to-end tests.

## Coding Style & Naming Conventions
- TypeScript is the primary language; follow strict compiler settings from `tsconfig.json`.
- Use modern ES module syntax and keep `type: module` compatibility in mind.
- Prefer descriptive, lowerCamelCase function and variable names.
- No formatter or linter is configured; keep code readable and consistent with existing style.

## Migration Guidance
- Do not introduce new product features in this legacy stack.
- Keep changes scoped to stabilization, bug fixes, and migration support.
- Implement new web product features in `src/webApp` (Compose for Web) once that module exists.

## Testing Guidelines
- Playwright is used for end-to-end tests; keep new UI flows covered with E2E tests.
- Run `bun run test:e2e` as part of the development lifecycle before shipping changes.
- E2E runs require `E2E_EMAIL` to be set so the auth flow can proceed.

## Development Workflow
- Develop locally: `bun install` then `bun run index.ts`.
- Add/update tests alongside features, keep `tests/` in sync with UI flows.
- Prefer migration-safe edits that reduce dependency on the legacy backend.

## Configuration & Safety Notes
- Generated files under `convex/_generated/` are build artifacts.
- Keep secrets out of the repo; use environment variables.
