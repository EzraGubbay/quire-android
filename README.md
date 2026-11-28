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

## Install with Obtainium

Releases are published as a single signed APK on [GitHub Releases](https://github.com/EzraGubbay/quire-android/releases),
which [Obtainium](https://obtainium.imranr.dev/) can install and update directly.

1. In Obtainium choose **Add App** and paste the repository URL: `https://github.com/EzraGubbay/quire-android`.
2. Leave the source as GitHub. Enable **Include prereleases** if you want `-rcN` builds.
3. Tap **Add**, then **Install**.

**Private repository.** While the repo is private, Obtainium needs a GitHub token to see releases. Create your own
fine-grained personal access token (GitHub > Settings > Developer settings), scoped to this repository only, with
read-only **Contents** permission, and paste it into Obtainium's GitHub source settings. The token lives only on your
phone: never commit it, put it in a file in this repo, or share it.

**Updates.** Obtainium checks for new releases periodically; open it, tap the Quire entry and choose **Update**.
Android only accepts an update signed with the same key as the installed app, so the certificate never changes.
The app also checks GitHub Releases at most every 6 hours and, when a newer version is out, offers to open Obtainium on Quire's page (Obtainium 1.7.5+; older versions open Obtainium's home screen).

**Verify the certificate fingerprint.** Compare the SHA-256 of the APK's signing certificate with the one you recorded
when the key was generated (`keytool -list -v -keystore quire-release.jks`):

```
apksigner verify --print-certs quire-android-<version>.apk   # look for "certificate SHA-256 digest"
```

Maintainers can check a published release against all of Obtainium's requirements (one `.apk`, stable `applicationId`
and certificate, `versionName` equals the tag without `v`, strictly increasing `versionCode`) with
`scripts/check-release.sh <tag>`; see [docs/RELEASING.md](docs/RELEASING.md).
