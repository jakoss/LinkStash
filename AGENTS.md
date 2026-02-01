# Repository Guidelines

## Project Overview
LinkStash is a simple link-saving web app for quickly stashing URLs to review later, optimized for mobile sharing. The core flow is: share a URL from your phone to the app, store it in a default space, and later organize it into other spaces. A Convex Android client will also be used to support a native share-to-save experience.

Key features to keep easy and accessible:
- Add a new URL
- Create a new space
- Remove a space
- Rename a space
- Move a URL to another space
- Mark a link as read (archive it)
- Toggle between active and archived links for historical browsing

## Product Requirements
- Default space: all new links land in a single default space unless the user explicitly chooses another.
- Archiving: marking a link as read moves it to the archived list; archived links remain searchable and viewable.
- Spaces are lightweight buckets for organizing links; moving links between spaces should be a single, quick action.

## Project Structure & Module Organization
- `web/` contains the Bun + Convex web app codebase.
- `mobile/` is reserved for the Android client (Convex Android integration).
- `AGENTS.md` describes repository-wide expectations and product goals.

## Build, Test, and Development Commands
- Web app commands live under `web/` (see `web/AGENTS.md`).
- Android client commands will be documented under `mobile/` once added.

## Coding Style & Naming Conventions
- TypeScript is the primary language; follow strict compiler settings from `tsconfig.json`.
- Use modern ES module syntax and keep `type: module` compatibility in mind.
- Prefer descriptive, lowerCamelCase function and variable names.
- No formatter or linter is configured; keep code readable and consistent with existing style.

## Testing Guidelines
- No test framework or test directory is currently configured.
- If you add tests, document the framework and add a runnable command in `package.json`.

## Commit & Pull Request Guidelines
- No Git history is present in this repository, so no commit conventions can be inferred.
- Suggested baseline: concise, present-tense messages (e.g., `Add convex query for links`).
- For PRs, include a brief summary, the rationale for the change, and any manual test notes.

## Configuration & Safety Notes
- Keep secrets out of the repo; use environment variables or platform-specific config as needed.
