# Releasing

`version.properties` (repo root) is the single source of the app version: `VERSION_NAME` (`MAJOR.MINOR.PATCH`,
optional `-rcN`) and `VERSION_CODE` (positive integer). `app/build.gradle.kts` reads it.

- Check: `./gradlew verifyVersion printVersion` (prints e.g. `0.1.0 (1)`).
- The release tag is `v<VERSION_NAME>`.
- `VERSION_CODE` strictly increases on every release.
- The bump happens in the `dev-rc` -> `main` release PR.
