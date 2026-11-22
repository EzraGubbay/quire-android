# Releasing

`version.properties` (repo root) is the single source of the app version: `VERSION_NAME` (`MAJOR.MINOR.PATCH`,
optional `-rcN`) and `VERSION_CODE` (positive integer). `app/build.gradle.kts` reads it.

- Check: `./gradlew verifyVersion printVersion` (prints e.g. `0.1.0 (1)`).
- The release tag is `v<VERSION_NAME>`.
- `VERSION_CODE` strictly increases on every release.
- The bump happens in the `dev-rc` -> `main` release PR.

## Signing the release APK

Release builds are signed with a key that never lives in the repo. `./gradlew assembleRelease` reads four values, from
environment variables (CI) or from an untracked `keystore.properties` at the repo root (local; same key names):

`QUIRE_KEYSTORE_FILE`, `QUIRE_KEYSTORE_PASSWORD`, `QUIRE_KEY_ALIAS`, `QUIRE_KEY_PASSWORD`

Per key the environment wins. If any is missing, `assembleRelease` fails immediately naming the missing keys; debug builds
and all checks are unaffected. APK Signature Schemes v1, v2 and v3 are enabled.

`./gradlew verifyNoTrackedSecrets` fails if a `*.jks`, `*.keystore`, `*.p12`, `keystore.properties` or
`google-services.json` file is tracked by git (all are in `.gitignore`).

### Generate the keystore (human only, once)

Do this on your own machine, never via an agent or in the repo:

```
keytool -genkeypair -v -keystore quire-release.jks -alias <alias> -keyalg RSA -keysize 4096 -validity 10000
```

Choose the store and key passwords yourself. Back the `.jks` file and both passwords up in a password manager (and an
offline copy): losing the key means existing installs can never be updated, because Android only accepts updates signed
by the same key.

### GitHub Actions secrets to set (names only)

- `QUIRE_KEYSTORE_BASE64` -- the keystore file, base64-encoded (`base64 -i quire-release.jks | pbcopy`)
- `QUIRE_KEYSTORE_PASSWORD`
- `QUIRE_KEY_ALIAS`
- `QUIRE_KEY_PASSWORD`

The release workflow decodes `QUIRE_KEYSTORE_BASE64` to a temp file and exports it as `QUIRE_KEYSTORE_FILE`.

### Local signed build (optional)

Create `keystore.properties` (git-ignored) with the four `QUIRE_*` keys above, then run `./gradlew assembleRelease`.
