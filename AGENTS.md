# AGENTS.md

## Project

Quire for Android: the native phone client for Quire, a self-hosted study workspace (courses, lecture/recitation
decks, documents, notes, AI chat). The server is the Quire web app (`EzraGubbay/quire`, `apps/web`); this app talks to
it only through the versioned mobile API (`/api/mobile/v1`). One user today; nothing in the app may assume that.

Initiative: **QMOB — Quire Mobile** (GitHub Project 3). Android ships first and completely; iOS follows.

The one thing that must never break: **the user can open a course and read a file** — including offline once it is
downloaded.

## Scope: reader-first

In scope: pair with a server, browse courses / sessions / files (labelled lecture, recitation, homework, exam, book,
paper, other), view PDFs and decks, **downloads** for offline use, read notes, **AI chat** with streaming answers,
background sync and upload, push notifications (late, deferred epic).

Not in scope — do not build, even partially: writing or editing notes, annotations/highlights, mind-map, homework and
test modules, course management, research projects.

UI must be **phone-native and aesthetic**: Material 3, edge-to-edge, predictive back, RTL-safe (Hebrew course names),
TalkBack semantics. It is not a shrunk web UI — take the palette from the web app, never its layouts.

## Stack

- **Native only: Kotlin + Jetpack Compose.** No React Native, Flutter, KMP, WebView-wrapped web UI.
- Single Gradle module `:app`, Kotlin DSL, version catalog (`gradle/libs.versions.toml`). JDK 17.
- `applicationId` / namespace `com.ezragubbay.quire`. `minSdk 29`, `compileSdk` / `targetSdk 36`.
- Libraries by role (add each only when its story needs it): Hilt (DI), Retrofit + OkHttp + kotlinx.serialization
  (network), Room (cache), DataStore (settings/session), WorkManager (downloads, sync, upload), Navigation Compose,
  platform `PdfRenderer` (PDF). Tests: JUnit, Turbine, Robolectric, MockWebServer, Compose UI test, Roborazzi.
- Package layout under `app/src/main/java/com/ezragubbay/quire/`:
  `core/designsystem`, `core/network`, `core/database`, `core/data`, `core/download`, `core/upload`,
  `feature/<name>` (auth, courses, search, viewer, downloads, notes, chat, settings, upload, update), `navigation`, `sync`,
  `push`, `di`.

## Commands

The Gradle project is created by issue QMOB-E0-S1; until it merges these are the **intended** commands (CI already
expects exactly these — see `docs/CI-SPEC.md`). After E0 they are real; if a story changes one, it updates this file.

- Build: `./gradlew assembleDebug`
- Lint: `./gradlew ktlintCheck detekt lintDebug` (auto-format: `./gradlew ktlintFormat`)
- Unit tests: `./gradlew testDebugUnitTest` (one class: `--tests '*ClassName*'`)
- Screenshot tests: `./gradlew verifyRoborazziDebug` (re-record: `recordRoborazziDebug`)
- Instrumented / e2e (emulator, API 34): `./gradlew connectedDebugAndroidTest`
- Everything a PR into `dev-rc` needs: `./gradlew assembleDebug ktlintCheck detekt lintDebug testDebugUnitTest`

## Branches and merging — hard rules

```
agent/<issue>-<slug>  ──PR──▶  dev-rc  ──release PR──▶  main  ──tag v<version>──▶  GitHub Release
```

- Every change is a feature branch + pull request **into `dev-rc`**. Never commit or push to `dev-rc` or `main`.
- **Nothing merges into `dev-rc` unless `build`, `lint` and `unit-tests` pass.**
- **Nothing merges into `main` unless CI and e2e pass** (`build`, `lint`, `unit-tests`, `e2e`). `dev-rc` → `main`
  happens only as a release PR when `dev-rc` is shippable — once per sprint (1 sprint = 1 release).
- Agents never merge. Only the human merges, tags and releases.
- Do not weaken, skip, or delete a test or a check to get green. If a check is wrong, say so on the issue and stop.

## Test-driven development

- **Per user story:** define the unit tests first and commit them failing, then implement, then refactor. The issue's
  "Tasks" section names the tests to start from.
- **Per epic:** the epic's e2e journey is written before any of its stories (the `S0` story), as instrumented tests
  annotated `@Ignore("QMOB-E<n>")` against `MockWebServer` fixtures and stable test tags (`docs/e2e/`). A story removes
  the `@Ignore` from the cases it satisfies. An epic is done when none of its `@Ignore`s remain; a release PR into
  `main` fails while any `@Ignore("QMOB-…")` exists.
- UI tests find nodes by test tag (kebab-case), never by display text.
- Network behaviour is tested against the vendored contract fixtures in `app/src/test/resources/contract/mobile-v1/`
  (source: `docs/api/fixtures/mobile-v1/` in `EzraGubbay/quire`). Never hand-edit them to make a test pass.

## Working rules

- Work only on the issue you were given: **one issue, one branch, one PR, one implementer.**
- Only pick up issues that are labelled `agent-ready`, are in the currently **open** milestone (sprint), and have no
  open blockers. Skip `human-only` and `needs-spec`.
- Branch name: `agent/<issue-number>-<short-slug>` (the Claude action's auto-named `agent/issue-<n>-…` is fine).
- Open PRs as drafts into `dev-rc`. The PR body says `Closes #<n>` and ticks each acceptance criterion with evidence.
- If acceptance criteria are ambiguous or contradict this file, comment on the issue with your question and stop.
- Stay inside the issue's "Where to work". No drive-by refactors, reformatting, renames or dependency bumps.
- Never edit `.github/` (workflows, rulesets, CODEOWNERS), secrets, or CI configuration. Stories that need a workflow
  change are `human-only`.
- Write `@Annotations` and other `@words` in backticks in issues, PRs and comments so GitHub does not notify people.

## Secrets

Never create, read, print or commit secrets: signing keystore and passwords, `google-services.json`, Firebase service
accounts, device tokens, GitHub tokens. Their setup is human-only. For verification use throwaway values generated in
the build directory. If you find a secret in the repo or in logs, stop and report it on the issue.

## Definition of done

- Every acceptance criterion is ticked in the PR body, with evidence where the criterion asks for it.
- Tests were written first and are in the PR; the issue's verification command passes locally.
- `build`, `lint` and `unit-tests` are green on the PR; e2e cases owned by the story are un-ignored and pass.
- No changes outside the issue's scope; docs touched by the change are updated (`docs/`, this file's Commands).

## Planning structure

- Hierarchy: **Epic** (parent issue, type Feature, label `epic`) → **User story** (sub-issue, label `story`, size
  XS–M; anything larger is split) → **Tasks** (the ordered list inside the story).
- Sprints are milestones; one sprint = one release. Plan and checkpoints: `docs/SPRINTS.md`. Backlog:
  `docs/BACKLOG.md`. CI contract: `docs/CI-SPEC.md`.
- Labels: `agent-ready`, `needs-spec`, `human-only`, `spike`, `agent:claude`, `agent:gemini`, `epic`, `story`,
  `task`, `android`.
