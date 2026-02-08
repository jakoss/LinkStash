# Phase 8 — Decommission Convex/Bun Web

## Goal
Remove the legacy Bun + Convex implementation once the new stack is stable.

## Scope
In scope:
- Delete/replace `web/` (Bun/Convex) after the new Compose web + Ktor server are shipping core flows
- Remove Convex-specific docs and env setup
- Update root `README.md` with the new dev commands

Out of scope:
- Refactors unrelated to the migration

## Tasks (checklist)
- [ ] Remove Convex/Bun web app:
  - [ ] Delete `web/` or replace with Compose web module reference
- [ ] Update documentation:
  - [ ] Root README: new “run server”, “run web”, “run android” instructions
  - [ ] Remove Convex env var guidance
- [ ] Repo cleanup:
  - [ ] Remove unused dependencies/config left over from Convex (only after verifying nothing depends on it)

## Deliverables
- Repo contains only Ktor + KMP + Compose apps as the supported stack.

## Acceptance criteria
- No Convex references remain in docs or build tooling.
- A clean “new developer onboarding” path exists in README.

