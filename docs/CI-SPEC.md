# CI test specification — quire-android

CI is defined **before** implementation. The workflow files already exist (`.github/workflows/ci.yml`,
`.github/workflows/release.yml`); each job stays skipped until the files it needs land in Epic 0, then it is binding.
This document is the contract: if the workflow and this spec disagree, fix the workflow.

## 1. The rules CI enforces

| Merge | Must pass | Enforced by |
|---|---|---|
| feature branch → `dev-rc` | `build`, `lint`, `unit-tests` | ruleset `dev-rc-hard-rules` (`.github/rulesets/dev-rc.json`) |
| `dev-rc` → `main` (release PR) | `build`, `lint`, `unit-tests`, `e2e`, branch up to date | ruleset `main-hard-rules` (`.github/rulesets/main.json`) |
| tag `v*` on `main` | tag = `version.properties`, unit tests, signed build | `release.yml` |

Job names are the required status-check contexts. Renaming a job means editing the ruleset JSON and re-applying it.

## 2. Triggers

| Event | build | lint | unit-tests | e2e |
|---|---|---|---|---|
| PR into `dev-rc` | run | run | run | — |
| PR into `main` | run | run | run | **run (required)** |
| push to `dev-rc` (after a merge) | run | run | run | run (early signal, not a gate) |
| `workflow_dispatch` | run | run | run | optional input `run_e2e` |

Runner: `ubuntu-latest`, Temurin JDK 17, `gradle/actions/setup-gradle` (build cache). Concurrency: one run per PR /
ref, older runs cancelled.

## 3. Jobs — what each must validate

### `detect` (always)
- Decides which jobs are live: Gradle wrapper + `settings.gradle.kts` → `build`, `unit-tests`;
  `config/detekt/detekt.yml` → `lint`; `app/src/androidTest/` → `e2e`.
- **Secret guard:** fails if any `*.jks`, `*.keystore`, `*.p12`, `keystore.properties` or `google-services.json` is
  tracked in git.

### `build` — Gradle build
- Command: `./gradlew assembleDebug`
- Proves: the project configures and compiles from a clean checkout with only the wrapper; all dependencies resolve
  from the version catalog; the debug APK is produced.
- Artifact: `quire-debug-apk` (7 days) — installable for manual checks.

### `lint` — ktlint + detekt + Android Lint
- Command: `./gradlew ktlintCheck detekt lintDebug --continue`
- ktlint: official Kotlin style via `.editorconfig`; Compose function naming allowed.
- detekt: `config/detekt/detekt.yml`, `maxIssues: 0`.
- Android Lint: `warningsAsErrors = true`, no baseline file. New warnings are fixed or suppressed locally with a
  reason, never baselined wholesale.
- Artifacts on failure: ktlint, detekt and lint HTML reports.

### `unit-tests` — JUnit on the JVM
- Command: `./gradlew testDebugUnitTest`
- Contains every test that needs no device:
  - **Pure unit tests** (JUnit, Turbine, coroutines-test): ViewModels, use cases, mappers, parsers, policies.
  - **Robolectric tests**: Room DAOs (in-memory), WorkManager workers (`TestListenableWorkerBuilder`), Compose
    component semantics.
  - **Network tests** (MockWebServer): interceptors, error mapping, streaming/NDJSON parsing, downloads with Range.
  - **Contract tests**: every DTO decodes the vendored fixtures in `app/src/test/resources/contract/mobile-v1/`
    (generated from the OpenAPI examples in `EzraGubbay/quire`, QMOB-E1-S9). A contract change shows up here first.
  - **Screenshot tests** (Roborazzi, from QMOB-E2-S4): verified inside this task via a Gradle property; goldens in
    `app/src/test/screenshots/`.
  - **Build-logic tests**: version validation (`verifyVersion`), tracked-secrets check, release-script checks.
- Rule: a story's unit tests are written first (TDD) and arrive in the same PR as the code.
- Artifacts: test reports, JUnit XML, Roborazzi diffs.

### `e2e` — instrumented tests on an emulator
- Command: `./gradlew connectedDebugAndroidTest`, run by `reactivecircus/android-emulator-runner@v2`.
- Emulator: API 34, `x86_64`, `google_apis`, KVM enabled, headless (`-no-window -gpu swiftshader_indirect -noaudio
  -no-boot-anim`), animations disabled, AVD snapshot cached by API/arch/target.
- Hermetic: the app talks only to `MockWebServer` serving fixtures from `app/src/androidTest/assets/fixtures/`
  (same shapes as the contract fixtures). No real server, no real AI, no secrets.
- Contains:
  - the smoke test (`MainActivity` shows `app-root`);
  - one **journey test class per epic**, written before the epic's stories and un-ignored story by story:

    | Epic | Journey test | Defined by |
    |---|---|---|
    | E2 shell | `ShellJourneyTest` | QMOB-E2-S0 |
    | E3 auth | `AuthJourneyTest` | QMOB-E3-S0 |
    | E4 browse | `CoursesJourneyTest` | QMOB-E4-S0 |
    | E5 reader + downloads | `ReaderJourneyTest` | QMOB-E5-S0 |
    | E6 chat | `ChatJourneyTest` | QMOB-E6-S0 |
    | E7 sync / upload | `SyncJourneyTest` | QMOB-E7-S0 |
    | E8 push | `PushJourneyTest` | QMOB-E8-S3 |
    | E9 release | `SmokeJourneyTest` on the minified build (added to this job by QMOB-E9-S3) | QMOB-E9-S2 |

  - device-only checks: real Keystore round trip, 300-page PDF stress test, process-death restoration.
- **Release gate** (PRs into `main` only): the job fails if any `@Ignore("QMOB` remains under `app/src/androidTest` —
  an epic whose e2e spec is still ignored is not shippable.
- Tests locate UI by test tag only (contract per epic in `docs/e2e/`).
- Artifacts: androidTest HTML reports and results (always).

### `release` (`release.yml`, tag `v*`) — verified in QMOB-E9-S3
- Tag is an ancestor of `main` and equals `VERSION_NAME` in `version.properties`.
- Runs unit tests, builds `assembleRelease` signed from Actions secrets (keystore decoded to the runner temp dir and
  removed afterwards), asserts exactly one APK.
- Publishes a GitHub Release with `quire-android-<version>.apk` + `.sha256`; tags containing `-` are pre-releases.
- Obtainium contract: one APK per release, stable `applicationId` and certificate, `versionName` = tag without `v`,
  increasing `versionCode` (`scripts/check-release.sh`, QMOB-E9-S4).

## 4. The mobile API side (EzraGubbay/quire)

Epic E1 is tested in the web repository's existing CI (`.github/workflows/ci.yml` there):
- `pnpm lint`, `pnpm typecheck`, `pnpm test` (vitest: `apps/web/src/lib/mobile/**`, OpenAPI example ↔ zod drift test,
  fixture staleness test).
- `pnpm --filter web test:e2e` (Playwright) including `e2e/mobile-api.spec.ts` — the epic's integration journey,
  defined first as `test.fixme` (QMOB-E1-S0), run with `AUTH_MODE=none`, `AI_MOCK=1`.

## 5. Human-only CI setup

- Repository Actions secrets: `CLAUDE_CODE_OAUTH_TOKEN` (agent + review workflows); later `QUIRE_KEYSTORE_BASE64`,
  `QUIRE_KEYSTORE_PASSWORD`, `QUIRE_KEY_ALIAS`, `QUIRE_KEY_PASSWORD` (E9); `GOOGLE_SERVICES_JSON_BASE64` (E8).
- Apply the two rulesets and create `dev-rc` (QMOB-E0-S4).
- Private-repo Actions minutes: the emulator job is the expensive one (~10–15 min). If minutes run short, drop the
  `push: dev-rc` trigger for `e2e` and rely on the release PR gate.

## 6. Not validated by CI (accepted gaps)

- Real-device behaviour (OnePlus 15R): camera QR scan, OEM battery restrictions on background work, Obtainium
  install/update — covered by the human acceptance in QMOB-E9-S5.
- Real server integration: covered by contract fixtures on both sides, not by a live cross-repo test.
- Real AI answers and real FCM delivery.
