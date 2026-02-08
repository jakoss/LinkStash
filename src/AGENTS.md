# Src Gradle Root Guidelines

## Project Overview
`src/` is the single Gradle root for LinkStash Kotlin projects. It hosts the Android client today and will expand with shared contracts, server, and Compose web modules.

## Base Technology
- Kotlin Multiplatform (KMP) is the foundation for shared models and app code.
- Ktor is the backend technology for the upcoming `server` module.
- Compose Multiplatform is used for UI modules (Android today, web later).

## Navigation
- We use the `nav3` library for navigation.

## Project Structure
- `settings.gradle.kts` and `gradlew` in `src/` define the shared build.
- `shared/` contains KMP shared code.
- `androidApp/` is the Android application.
- `contracts/` will contain shared API DTOs and error envelopes.
- `server/` will contain the Ktor backend.
- `webApp/` will contain the Compose for Web frontend.
- `iosApp/` exists but is not currently supported.
- `desktopApp/` is present but not a focus for product development.

## Build, Test, and Development Commands
- `./gradlew tasks` lists available Gradle tasks from `src/`.
- `./gradlew :androidApp:assembleDebug` builds the Android debug app.
- `./gradlew :shared:compileKotlinMetadata` verifies shared module compilation.
- `./gradlew :desktopApp:run` runs the desktop app (optional/local only).

## Development Notes
- Keep the Android share-to-save flow simple and fast.
- Mirror core product flows: add link to default space, move between spaces, and delete links (to Raindrop Trash).
- Keep new APIs and models aligned with contracts that will live in `contracts/`.
