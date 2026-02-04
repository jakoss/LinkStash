# Mobile App Guidelines

## Project Overview
The mobile client provides the share-to-save experience for LinkStash, optimized for Android. The iOS target exists in the repo but is intentionally out of scope for now.

## Base Technology
- Kotlin Multiplatform (KMP) is the foundation for shared logic and platform targets.
- Android is the only active target at the moment; iOS is ignored for now.

## Navigation
- We use the `nav3` library for navigation.

## Project Structure
- `shared/` contains KMP shared code.
- `androidApp/` is the Android application.
- `iosApp/` exists but is not currently supported.
- `desktopApp/` is present but not a focus for product development.

## Development Notes
- Keep the Android share-to-save flow simple and fast.
- Mirror the web app’s core flows: add link to default space, move between spaces, archive, and browse archived links.
