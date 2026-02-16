# Src Gradle Root Guidelines

## Project Overview
`src/` is the single Gradle root for LinkStash Kotlin projects. It hosts the Android client today and will expand with shared contracts, server, and Compose web modules.

## Base Technology
- Kotlin Multiplatform (KMP) is the foundation for shared models and app code.
- Ktor is the backend technology for the `server` module.
- Compose Multiplatform is used for UI modules (Android today, web later).

## Navigation
- We use the `nav3` library for navigation.

## Project Structure
- `settings.gradle.kts` and `gradlew` in `src/` define the shared build.
- `shared/` contains KMP shared code.
- `androidApp/` is the Android application.
- `contracts/` contains shared API DTOs and error envelopes.
- `server/` contains the Ktor backend.
- `webApp/` will contain the Compose for Web frontend.
- `iosApp/` exists but is not currently supported.
- `desktopApp/` is present but not a focus for product development.

## Build, Test, and Development Commands
- `./gradlew tasks` lists available Gradle tasks from `src/`.
- `./gradlew :server:compileKotlin` verifies server module compilation.
- `./gradlew :server:runServerLocal` runs the Ktor server with env vars loaded from `src/local.properties` (supports `ENV_NAME=value` or `server.env.ENV_NAME=value`). Use this as the default local run command.
- `DB_URL="jdbc:sqlite:/absolute/path/to/linkstash.db" SESSION_SECRET="..." TOKEN_HASHING_SECRET="..." RAINDROP_TOKEN_ENCRYPTION_KEY="..." RAINDROP_CLIENT_ID="..." RAINDROP_CLIENT_SECRET="..." RAINDROP_REDIRECT_URI="..." ./gradlew :server:run` runs the server with inline env vars when `runServerLocal` is not desired.
- Playwright auth E2E suite lives in `tests/e2e/` and runs against a real Raindrop OAuth test account configured via local env vars (kept out of git).
- `cd ../tests/e2e && bun run test:auth` runs the real OAuth auth flow E2E (start/exchange/me/logout/session revocation).
- `cd ../tests/e2e && bun run test` runs the whole Playwright suite.
- `./gradlew :androidApp:assembleDebug` builds the Android debug app.
- `./gradlew :shared:compileKotlinMetadata` verifies shared module compilation.
- `./gradlew :desktopApp:run` runs the desktop app (optional/local only).

## Development Notes
- Keep the Android share-to-save flow simple and fast.
- Mirror core product flows: add link to default space, move between spaces, and delete links (to Raindrop Trash).
- Keep new APIs and models aligned with contracts that will live in `contracts/`.
