# Testing

## Pyramid

| Layer | Where | Tools | Run |
|---|---|---|---|
| Unit (JVM) | `app/src/test/` | JUnit; Turbine, Robolectric, Roborazzi as stories add them | `./gradlew testDebugUnitTest` (one class: `--tests '*Name*'`) |
| Screenshot | `app/src/test/` | Roborazzi (E2-S4) | `./gradlew verifyRoborazziDebug` / `recordRoborazziDebug` |
| Instrumented / e2e | `app/src/androidTest/` | Compose UI test + `MockWebServer` on an emulator (API 34) | `./gradlew connectedDebugAndroidTest` |

Most logic belongs in unit tests; instrumented tests cover UI journeys end to end against fake servers. Tests are
written first (see `AGENTS.md`).

## Naming

- Classes: `<Subject>Test` (e.g. `MainActivitySmokeTest`, `MockWebServerRuleTest`).
- Methods: lowerCamelCase sentences describing behaviour (`servesFixtureBodyFromAssets`).
- Shared instrumented infrastructure lives in `app/src/androidTest/java/com/ezragubbay/quire/testing/`.

## Test tags

UI tests find nodes by test tag, never by display text. Tags are kebab-case (`app-root`, `course-list`,
`file-row-<id>`), set with `Modifier.testTag(...)`, and query with `onNodeWithTag`. Each epic's tag contract is
listed in `docs/e2e/` (one file per epic, `E<n>.md`).

## Instrumented harness

- `QuireTestRunner` is the `testInstrumentationRunner` (Hilt test application hook lands with E3-S1).
- `MockWebServerRule` starts a `MockWebServer` per test; `enqueueFixture("name.json", code = 200)` serves
  `app/src/androidTest/assets/fixtures/name.json`; `baseUrl` is what the app under test should call; `takeRequest()`
  returns recorded requests.
- Animations are disabled through `testOptions { animationsDisabled = true }`.
- A debug-only network security config permits cleartext to `localhost`/`127.0.0.1` so the app can reach the mock server.
- Contract-shaped fixtures for network unit tests live in `app/src/test/resources/contract/mobile-v1/` (vendored; never
  hand-edit).

## Epic e2e specs and `@Ignore`

An epic's e2e journey is written before its stories as instrumented tests marked `@Ignore("QMOB-E<n>")`. A story
removes the `@Ignore` from the cases it satisfies. A PR into `main` fails while any `@Ignore("QMOB` remains.

## Running

- Local: start an API 34 emulator (or device), then `./gradlew connectedDebugAndroidTest`.
- CI: the `e2e` job runs the same command on an emulator; required for `main`, also on pushes to `dev-rc` and by
  `workflow_dispatch` (input `run_e2e`).
- Reports: `app/build/reports/androidTests/connected/`.
