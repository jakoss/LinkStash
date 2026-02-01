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

## Coding Style & Naming Conventions
- TypeScript is the primary language; follow strict compiler settings from `tsconfig.json`.
- Use modern ES module syntax and keep `type: module` compatibility in mind.
- Prefer descriptive, lowerCamelCase function and variable names.
- No formatter or linter is configured; keep code readable and consistent with existing style.

## Testing Guidelines
- No test framework or test directory is currently configured.
- If you add tests, document the framework and add a runnable command in `package.json`.

## Configuration & Safety Notes
- Generated Convex files live under `convex/_generated/`; treat them as build artifacts.
- Keep secrets out of the repo; use environment variables or Convex config as needed.
