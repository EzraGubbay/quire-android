# Quire for Android

Native Android client (Kotlin + Jetpack Compose) for [Quire](https://github.com/EzraGubbay/quire): browse courses,
read decks and PDFs, keep them offline, and chat with the course AI. Reader-first — no note editing on mobile.

Status: Epic 0 in progress (project scaffold landed; empty Compose app builds).

- Rules for contributors and agents: [AGENTS.md](AGENTS.md)
- Backlog (epics → stories): [docs/BACKLOG.md](docs/BACKLOG.md)
- Sprints and release checkpoints: [docs/SPRINTS.md](docs/SPRINTS.md)
- CI test specification: [docs/CI-SPEC.md](docs/CI-SPEC.md)
- Board: GitHub Project "QMOB — Quire Mobile"

## Build

Requires JDK 17 and the Android SDK (platform 36); `local.properties` (SDK path) is git-ignored.

```
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # JVM unit tests
```
