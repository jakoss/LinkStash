# Repository Guidelines

## Project Overview
LinkStash is a lightweight link-saving app optimized for mobile sharing. The stack is migrating to:
- Ktor backend
- Raindrop.io as storage/auth upstream
- Kotlin Multiplatform shared contracts
- Compose for Web frontend
- Android share-to-save client

Key features to keep easy and accessible:
- Add a new URL
- Create a new space
- Remove a space
- Rename a space
- Move a URL to another space
- Delete a link (moves to Raindrop Trash)

## Product Requirements
- Default space: all new links land in a single default space unless the user explicitly chooses another.
- Deleting a link removes it from LinkStash and sends it to Raindrop Trash.
- Spaces are lightweight buckets for organizing links; moving links between spaces should be a single, quick action.

## Project Structure & Module Organization
- `src/` is the single Gradle root for Kotlin modules and apps.
  - current modules: `androidApp/`, `shared/`, `desktopApp/`, `iosApp/`
  - planned modules: `contracts/`, `server/`, `webApp/`
- `web/` contains the legacy Bun + Convex app that is being decommissioned in later migration phases.
- `AGENTS.md` describes repository-wide expectations and product goals.

## Build, Test, and Development Commands
- Kotlin/Gradle commands live under `src/` (see `src/AGENTS.md`).
- Legacy Bun web commands live under `web/` (see `web/AGENTS.md`).

## Coding Style & Naming Conventions
- Kotlin is the primary implementation language for new modules.
- TypeScript remains for legacy `web/` migration work.
- Prefer descriptive, lowerCamelCase function and variable names.
- No formatter or linter is configured; keep code readable and consistent with existing style.

## Testing Guidelines
- No test framework or test directory is currently configured.
- If you add tests, document the framework and add a runnable command in `package.json`.

## Commit & Pull Request Guidelines
- No Git history is present in this repository, so no commit conventions can be inferred.
- Suggested baseline: concise, present-tense messages (e.g., `Add space contracts`).
- For PRs, include a brief summary, the rationale for the change, and any manual test notes.

## Configuration & Safety Notes
- Keep secrets out of the repo; use environment variables or platform-specific config as needed.
