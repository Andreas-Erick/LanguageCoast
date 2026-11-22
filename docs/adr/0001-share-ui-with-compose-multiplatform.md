# ADR-0001: Share the UI, not only the logic, with Compose Multiplatform

- **Status:** Accepted
- **Date:** 2026-10-11
- **Commits:** `5a25fd1`, `4f7f25f`, `69cdbf6`, `db1bc74`

## Context

Language Coast started as a single-module Android app:
Jetpack Compose, Hilt and Room. An iOS version was wanted,
built and kept up by one person, without a second codebase
that drifts. The screens carry much of the product (the
study modes, the daily session, the route), and they change
every week.

## Options considered

1. **Shared logic, native UI.** Kotlin Multiplatform for
   `data/`, SwiftUI on iOS. Native look and feel, but every
   screen is written twice, and the iOS half falls behind.
2. **Shared logic and UI with Compose Multiplatform.** One
   set of screens and ViewModels in `commonMain`. Platform
   pieces sit behind `expect`/`actual`.
3. **Rewrite in Flutter or React Native.** One UI too, but
   it throws away working Kotlin and the Android libraries
   the app depends on (ML Kit, LiteRT-LM, WorkManager).

## Decision

Option 2, in steps that each kept the app working. Pure
logic moved into `shared` first (`5a25fd1`), then the
database, repositories and settings (`4f7f25f`), then every
screen (`69cdbf6`).

Hilt was replaced with Koin (`db1bc74`), because Hilt only
runs on Android. `KoinModulesTest` now checks the
dependency graph that Hilt used to check at compile time.

## Consequences

- One feature is one change, and iOS gets new screens the
  same day.
- Android-only features stay Android-only, and the README
  names them: ML Kit, Local AI (LiteRT-LM), practice sheet
  PDFs, OCR and the widget. On iOS, translation needs a
  cloud key.
- The iOS app is Compose, not SwiftUI. It follows Material,
  not every iOS convention.
- A missing Koin binding fails a test in CI, not the
  compile.
