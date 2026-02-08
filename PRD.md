# LinkStash PRD (Lean v1)

## Product Summary
LinkStash is a simple link-saving web and Android app optimized for mobile sharing. Users quickly stash URLs to review later, starting in a default space and later organizing into lightweight spaces. The Android client provides native share-to-save in v1, while the web app supports manual paste.

## Primary User
Individual knowledge workers who capture links quickly and review them later.

## Problem
Capturing links on mobile is frictionful and existing tools feel heavy for quick, single-action saving and later sorting.

## Goals
- Make “save a link” a fast, low-friction action from mobile share.
- Keep organization lightweight with quick moves between spaces.
- Support a clean delete flow for processed links (deleted links go to Raindrop Trash).

## Core User Flow
1. Android: share a URL to LinkStash via native share.
2. Web: manually paste a URL to save it.
3. Link is saved into the default space.
4. Later, user moves it to another space or deletes it.
5. Deleted links remain recoverable through Raindrop Trash outside of LinkStash.

## MVP Features (v1)
- Add a new URL.
- Default space for all new links.
- Create, rename, and remove a space.
- Move a URL to another space as a single quick action.
- Delete a link (moves it to Raindrop Trash).
- Android client with native share-to-save.
- Android client offline add: store link locally and sync when online.
- Web-only: export all links from the current space to clipboard, separated by newlines.
- Capture basic Open Graph metadata for saved links (when available).

## Non-Goals (v1)
- Collaboration or shared spaces.
- Advanced tagging, notes, or metadata enrichment.
- Browser extensions or desktop capture.
- Offline-first experiences.
- Search.

## Product Requirements
- All new links land in the default space unless the user chooses another.
- Deleting a link removes it from LinkStash lists and moves it to Raindrop Trash.
- Spaces are lightweight buckets; moving links should be a single, quick action.
- Only the default space is pre-created.
- Android client must support native share-to-save in v1.
- Android client must support offline add by saving to local database and syncing when connectivity returns.
- Offline sync de-duplicates by URL.
- Links are ordered newest-first by default.
- Deleting a space deletes all links within it.
- Deleting a space requires confirmation with an input that matches the space name.

## Technical Constraints
- Backend built with Ktor and integrates with Raindrop APIs.
- Shared contracts are implemented in Kotlin Multiplatform modules.
- Web client is migrating to Compose for Web.
- Android client uses Kotlin Multiplatform and supports native share-to-save.

## Open Questions
- None for now.
