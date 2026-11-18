# QMOB backlog — Quire Mobile (Android first)

Generated from the planning data on 2026-11-19; **GitHub issues are the source of truth** — when they disagree with
this file, the issue wins. Board: [QMOB — Quire Mobile](https://github.com/users/EzraGubbay/projects/3).

- Hierarchy: **Epic** (parent issue, type Feature) → **User story** (sub-issue; 1 story = 1 PR = 1 implementer; size XS–M) → **Tasks** (ordered list inside the story, tests first).
- Every story has: Goal, acceptance criteria, verification command, where to work, out of scope, size.
- `agent-ready` = fully specified, an agent may start when its sprint is open. `needs-spec` = open questions listed in the issue. `human-only` = needs repository settings, secrets, workflows or a physical device.
- E1 stories live in `EzraGubbay/quire` (the code is in `apps/web`); everything else is in this repository.
- Epic-level TDD: each epic starts with an `S0` story that writes the e2e journey first.

## Epics

| Epic | Issue | Title | Sprint | Stories | Ready / needs-spec / human |
|---|---|---|---|---|---|
| E0 | [#1](https://github.com/EzraGubbay/quire-android/issues/1) | Repo scaffold + CI/CD pipeline | 1 | 5 | 4 / 0 / 1 |
| E1 | [#2](https://github.com/EzraGubbay/quire-android/issues/2) | Mobile API layer in apps/web (`/api/mobile/v1`) | 1 | 10 | 9 / 1 / 0 |
| E2 | [#3](https://github.com/EzraGubbay/quire-android/issues/3) | App shell: navigation + phone-native design system | 2 | 7 | 6 / 1 / 0 |
| E3 | [#4](https://github.com/EzraGubbay/quire-android/issues/4) | Auth: device pairing against the mobile API | 2 | 6 | 6 / 0 / 0 |
| E4 | [#5](https://github.com/EzraGubbay/quire-android/issues/5) | Course & file browser (reader) with type labels | 3 | 6 | 6 / 0 / 0 |
| E5 | [#6](https://github.com/EzraGubbay/quire-android/issues/6) | Document viewer (PDF/decks) + downloads | 3 | 8 | 7 / 1 / 0 |
| E6 | [#7](https://github.com/EzraGubbay/quire-android/issues/7) | AI chat (streaming) | 4 | 7 | 7 / 0 / 0 |
| E7 | [#8](https://github.com/EzraGubbay/quire-android/issues/8) | Background upload / sync | 5 | 6 | 3 / 3 / 0 |
| E8 | [#9](https://github.com/EzraGubbay/quire-android/issues/9) | Push notifications (Firebase) — deferred checkpoint | 6 | 6 | 1 / 4 / 1 |
| E9 | [#10](https://github.com/EzraGubbay/quire-android/issues/10) | Release: signing, signed APK, GitHub Release, Obtainium | 5 | 5 | 3 / 0 / 2 |

Totals: 10 epics, 66 stories — 52 `agent-ready`, 10 `needs-spec`, 4 `human-only`.

## E0 — Repo scaffold + CI/CD pipeline ([#1](https://github.com/EzraGubbay/quire-android/issues/1))

**Goal.** A buildable Kotlin + Jetpack Compose project whose CI pipeline (build, lint, unit, emulator e2e) and branch rules enforce the hard rules before any feature work starts.

**Epic e2e / integration tests (defined first).** The pipeline is the integration test: (1) a PR with a failing unit test cannot merge into `dev-rc`; (2) a `dev-rc` → `main` PR cannot merge unless `build`, `lint`, `unit-tests` and `e2e` are green; (3) the emulator smoke test launches `MainActivity` and finds `app-root`. See `docs/CI-SPEC.md`.

**Done when.** All four CI jobs run (not skipped) and pass on `dev-rc`; both rulesets active; `docs/TESTING.md` and `docs/RELEASING.md` exist.

**Out of scope.** Feature UI, networking, DI, signing/release workflow verification (E9).

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E0-S1 | [#11](https://github.com/EzraGubbay/quire-android/issues/11) | Gradle scaffold: single-activity Kotlin + Compose app | S | 1 | `agent-ready` | — |
| E0-S2 | [#12](https://github.com/EzraGubbay/quire-android/issues/12) | Static analysis: ktlint + detekt + Android Lint | S | 1 | `agent-ready` | E0-S1 |
| E0-S3 | [#13](https://github.com/EzraGubbay/quire-android/issues/13) | Instrumented test harness: Compose UI tests + MockWebServer on an emulator | S | 1 | `agent-ready` | E0-S1 |
| E0-S4 | [#14](https://github.com/EzraGubbay/quire-android/issues/14) | Branch model + rulesets: dev-rc and main hard rules | XS | 1 | `human-only` | — |
| E0-S5 | [#15](https://github.com/EzraGubbay/quire-android/issues/15) | Versioning: versionName / versionCode from version.properties | XS | 1 | `agent-ready` | E0-S1 |

## E1 — Mobile API layer in apps/web (`/api/mobile/v1`) ([#2](https://github.com/EzraGubbay/quire-android/issues/2))

**Goal.** A stable, versioned, documented JSON API plus mobile auth in the Quire web app, so the native apps never depend on web-internal routes or server actions. Mostly *complete + unify + auth + tests/docs*: courses/files JSON already exists in `apps/web/src/lib/mcp/courses.ts`, file streaming in `/api/projects/[slug]/documents/[docId]/file`, chat streaming in `/api/projects/[slug]/chat/[threadId]`.

**Epic e2e / integration tests (defined first).** `apps/web/e2e/mobile-api.spec.ts` (Playwright `request` fixture, `AI_MOCK=1`): pair → `/me` → courses → files → full + ranged download + 304 → notes → create thread → streamed answer → revoke → 401. Written first as `test.fixme` (QMOB-E1-S0); each story un-fixmes its steps.

**Done when.** No `test.fixme` left except upload (QMOB-E1-S8); OpenAPI + fixtures + `docs/mobile-api.md` merged; deployed by the existing web deploy workflow on merge to `main`.

**Out of scope.** Note editing, mind-map, homework/tests modules, research projects, changing existing web routes' behaviour, OAuth/PKCE, multi-user data scoping (must not be precluded).

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E1-S0 | [quire#31](https://github.com/EzraGubbay/quire/issues/31) | Define the mobile API integration suite first (Playwright API spec, fixme) | S | 1 | `agent-ready` | — |
| E1-S1 | [quire#32](https://github.com/EzraGubbay/quire/issues/32) | Contract: OpenAPI 3.1 for /api/mobile/v1 + error envelope + zod schemas | S | 1 | `agent-ready` | — |
| E1-S2 | [quire#33](https://github.com/EzraGubbay/quire/issues/33) | Mobile auth: per-device tokens + pairing-code exchange | M | 1 | `agent-ready` | E1-S0, E1-S1 |
| E1-S3 | [quire#34](https://github.com/EzraGubbay/quire/issues/34) | Settings → Mobile devices: pairing code + QR, list and revoke devices | S | 1 | `agent-ready` | E1-S2 |
| E1-S4 | [quire#35](https://github.com/EzraGubbay/quire/issues/35) | Courses, sessions and files endpoints (labelled JSON) | S | 1 | `agent-ready` | E1-S1, E1-S2 |
| E1-S5 | [quire#36](https://github.com/EzraGubbay/quire/issues/36) | File content endpoint: streaming with Range and ETag | S | 1 | `agent-ready` | E1-S4 |
| E1-S6 | [quire#37](https://github.com/EzraGubbay/quire/issues/37) | Notes read endpoints (Markdown, read-only) | S | 1 | `agent-ready` | E1-S1, E1-S2 |
| E1-S7 | [quire#38](https://github.com/EzraGubbay/quire/issues/38) | Chat endpoints: threads, messages, NDJSON streaming answer | M | 1 | `agent-ready` | E1-S1, E1-S2 |
| E1-S8 | [quire#39](https://github.com/EzraGubbay/quire/issues/39) | Upload endpoint for mobile (async, resumable) | M | 5 | `needs-spec` | E1-S2 |
| E1-S9 | [quire#40](https://github.com/EzraGubbay/quire/issues/40) | Mobile API docs + contract fixtures for the apps | S | 1 | `agent-ready` | E1-S3, E1-S4, E1-S5, E1-S6, E1-S7 |

Open questions:
- E1-S8: Blocked on the web+desktop async-upload design (handled separately): job model, status endpoint, size limit.
- E1-S8: Resumability: single streamed request with idempotency key, or chunked/tus-style?
- E1-S8: Classification fields: type (lecture / recitation / other), optional session — required or inferred from the filename like the web upload?

## E2 — App shell: navigation + phone-native design system ([#3](https://github.com/EzraGubbay/quire-android/issues/3))

**Goal.** A phone-native, aesthetic shell — bottom navigation, Quire theme, reusable components — designed for a handheld reader, not a shrunk web UI.

**Epic e2e / integration tests (defined first).** `ShellJourneyTest` (QMOB-E2-S0): launch → Courses is the start destination → switch tabs (state kept) → back behaviour → Settings → change theme → relaunch persists.

**Done when.** No `@Ignore("QMOB-E2")` remains; Roborazzi goldens committed; contrast unit tests green in light and dark.

**Out of scope.** Real data, auth, tablet/foldable adaptive layouts, widgets, Wear.

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E2-S0 | [#16](https://github.com/EzraGubbay/quire-android/issues/16) | Define the E2 e2e journey first (shell): ignored specs + test-tag contract | XS | 2 | `agent-ready` | E0-S3 |
| E2-S1 | [#17](https://github.com/EzraGubbay/quire-android/issues/17) | Design tokens + QuireTheme (color, typography, shape, spacing) | S | 2 | `agent-ready` | E0-S1, E0-S2 |
| E2-S2 | [#18](https://github.com/EzraGubbay/quire-android/issues/18) | Navigation scaffold: bottom bar (Courses · Downloads · Chats) + Settings | S | 2 | `agent-ready` | E2-S0, E2-S1 |
| E2-S3 | [#19](https://github.com/EzraGubbay/quire-android/issues/19) | Core component kit: top bar, course card, file row + type chip, state views | M | 2 | `agent-ready` | E2-S1 |
| E2-S4 | [#20](https://github.com/EzraGubbay/quire-android/issues/20) | Screenshot regression tests for the design system (Roborazzi) | S | 2 | `agent-ready` | E2-S3 |
| E2-S5 | [#21](https://github.com/EzraGubbay/quire-android/issues/21) | Settings screen: theme mode, device colors, about | S | 2 | `agent-ready` | E2-S2 |
| E2-S6 | [#22](https://github.com/EzraGubbay/quire-android/issues/22) | App identity: adaptive icon, themed (monochrome) icon, splash screen | XS | 2 | `needs-spec` | E2-S1 |

Open questions:
- E2-S6: Needs a vector logo: the web app only ships raster icons (`apps/web/public/icons/icon-512.png`). Human to supply an SVG / vector mark, or approve the agent redrawing one.

## E3 — Auth: device pairing against the mobile API ([#4](https://github.com/EzraGubbay/quire-android/issues/4))

**Goal.** The user pairs the phone once (QR or code from the web Settings page) and stays signed in securely; an invalid/revoked token returns them to pairing.

**Epic e2e / integration tests (defined first).** `AuthJourneyTest` (QMOB-E3-S0): fresh install → Pair screen → pair via MockWebServer → Courses; relaunch stays signed in; 401 → back to Pair; sign out from Settings.

**Done when.** No `@Ignore("QMOB-E3")` remains; contract tests decode the vendored `mobile-v1` fixtures; token never appears in logs or backups.

**Out of scope.** GitHub OAuth in the app, multi-account, biometrics/app lock.

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E3-S0 | [#23](https://github.com/EzraGubbay/quire-android/issues/23) | Define the E3 e2e journey first (auth): ignored specs + test-tag contract | XS | 2 | `agent-ready` | E0-S3 |
| E3-S1 | [#24](https://github.com/EzraGubbay/quire-android/issues/24) | Network core: API client, auth interceptor, error envelope → ApiResult | S | 2 | `agent-ready` | E0-S3, E1-S2, E1-S9 |
| E3-S2 | [#25](https://github.com/EzraGubbay/quire-android/issues/25) | Secure session storage (Keystore-encrypted token) | S | 2 | `agent-ready` | E3-S1 |
| E3-S3 | [#26](https://github.com/EzraGubbay/quire-android/issues/26) | Pairing screen: server URL + code entry | S | 2 | `agent-ready` | E3-S0, E3-S1, E3-S2, E2-S2 |
| E3-S4 | [#27](https://github.com/EzraGubbay/quire-android/issues/27) | QR pairing + quire://pair deep link | S | 2 | `agent-ready` | E3-S3 |
| E3-S5 | [#28](https://github.com/EzraGubbay/quire-android/issues/28) | Session lifecycle: expiry handling, sign-out, account section in Settings | S | 2 | `agent-ready` | E3-S3, E2-S5 |

## E4 — Course & file browser (reader) with type labels ([#5](https://github.com/EzraGubbay/quire-android/issues/5))

**Goal.** Browse courses, their lectures/recitations, files (labelled lecture / recitation / homework / exam / book / paper / other) and notes list — fast, offline-first, searchable.

**Epic e2e / integration tests (defined first).** `CoursesJourneyTest` (QMOB-E4-S0): course list grouped by semester → open course → sessions timeline → files filter by type → search → relaunch offline shows cached data with a banner.

**Done when.** No `@Ignore("QMOB-E4")` remains; everything browsable offline after one successful sync.

**Out of scope.** Creating/editing/moving anything, research projects, mind-map, homework/tests modules, opening files (E5).

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E4-S0 | [#29](https://github.com/EzraGubbay/quire-android/issues/29) | Define the E4 e2e journey first (browse): ignored specs + test-tag contract | S | 3 | `agent-ready` | E0-S3 |
| E4-S1 | [#30](https://github.com/EzraGubbay/quire-android/issues/30) | Offline-first data layer: Room cache + repositories for courses, sessions, files | M | 3 | `agent-ready` | E3-S1, E1-S4 |
| E4-S2 | [#31](https://github.com/EzraGubbay/quire-android/issues/31) | Courses screen: semester-grouped list with refresh and states | S | 3 | `agent-ready` | E4-S0, E4-S1, E2-S3, E3-S5 |
| E4-S3 | [#32](https://github.com/EzraGubbay/quire-android/issues/32) | Course screen: Sessions · Files · Notes tabs | S | 3 | `agent-ready` | E4-S2, E1-S6 |
| E4-S4 | [#33](https://github.com/EzraGubbay/quire-android/issues/33) | Files tab: type labels, filter chips, sort, file info sheet | S | 3 | `agent-ready` | E4-S3 |
| E4-S5 | [#34](https://github.com/EzraGubbay/quire-android/issues/34) | Search across courses and files (cached, offline) | S | 3 | `agent-ready` | E4-S4 |

## E5 — Document viewer (PDF/decks) + downloads ([#6](https://github.com/EzraGubbay/quire-android/issues/6))

**Goal.** Open any PDF or deck in a smooth native viewer and keep files offline with reliable, resumable downloads.

**Epic e2e / integration tests (defined first).** `ReaderJourneyTest` (QMOB-E5-S0): open a lecture deck → page indicator → scroll → reopen resumes the page → download → works with the server down → Downloads tab lists it → remove.

**Done when.** No `@Ignore("QMOB-E5")` remains; 300-page stress test passes without OOM; ADR 0001 merged.

**Out of scope.** Annotations/highlights, text selection or in-PDF search, in-app PPTX/Keynote rendering, note editing.

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E5-S0 | [#35](https://github.com/EzraGubbay/quire-android/issues/35) | Define the E5 e2e journey first (reader): ignored specs + test-tag contract | S | 3 | `agent-ready` | E0-S3 |
| E5-S1 | [#36](https://github.com/EzraGubbay/quire-android/issues/36) | Download engine: resumable WorkManager downloads with Room state | M | 3 | `agent-ready` | E4-S1, E1-S5 |
| E5-S2 | [#37](https://github.com/EzraGubbay/quire-android/issues/37) | PDF viewer: smooth paging, zoom, resume position | M | 3 | `agent-ready` | E5-S0, E2-S3 |
| E5-S3 | [#38](https://github.com/EzraGubbay/quire-android/issues/38) | Open-file flow: stream-to-cache, download toggle, originals via open-with | S | 3 | `agent-ready` | E5-S1, E5-S2, E4-S4 |
| E5-S4 | [#39](https://github.com/EzraGubbay/quire-android/issues/39) | Downloads tab: offline library, storage use, remove / retry, progress notification | S | 3 | `agent-ready` | E5-S3 |
| E5-S5 | [#40](https://github.com/EzraGubbay/quire-android/issues/40) | Share / save a copy of a downloaded file | XS | 3 | `agent-ready` | E5-S4 |
| E5-SP | [#41](https://github.com/EzraGubbay/quire-android/issues/41) | Spike: Markdown + LaTeX rendering approach for notes and chat answers | S | 3 | `agent-ready` | — |
| E5-S6 | [#42](https://github.com/EzraGubbay/quire-android/issues/42) | Notes reader (read-only Markdown with math) | M | 3 | `needs-spec` | E5-SP, E4-S3, E1-S6 |

Open questions:
- E5-S6: Rendering approach — blocked by the spike QMOB-E5-SP (ADR 0001).
- E5-S6: Wiki-link resolution: the API returns raw `[[name]]`; resolve client-side by title, or add a resolve field/endpoint to the v1 contract?
- E5-S6: How should Quire-specific term callouts (theorem / definition / lemma …) be rendered on mobile?

## E6 — AI chat (streaming) ([#7](https://github.com/EzraGubbay/quire-android/issues/7))

**Goal.** Ask Quire's AI about a course or a specific document and read the answer as it streams, with tappable citations.

**Epic e2e / integration tests (defined first).** `ChatJourneyTest` (QMOB-E6-S0): new thread → send → streamed answer grows → citation chip opens the viewer at the page → error fixture → retry succeeds → thread appears in Chats.

**Done when.** No `@Ignore("QMOB-E6")` remains; parser unit tests cover split chunks and split UTF-8.

**Out of scope.** Voice, attachments, message editing, model/mode pickers beyond passthrough, offline question queue.

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E6-S0 | [#43](https://github.com/EzraGubbay/quire-android/issues/43) | Define the E6 e2e journey first (chat): ignored specs + test-tag contract | S | 4 | `agent-ready` | E0-S3 |
| E6-S1 | [#44](https://github.com/EzraGubbay/quire-android/issues/44) | Chat data layer: threads/messages API + NDJSON stream → Flow<ChatEvent> | M | 4 | `agent-ready` | E3-S1, E1-S7, E1-S9 |
| E6-S2 | [#45](https://github.com/EzraGubbay/quire-android/issues/45) | Thread lists: per-course chats and the global Chats tab | S | 4 | `agent-ready` | E6-S0, E6-S1, E4-S3 |
| E6-S3 | [#46](https://github.com/EzraGubbay/quire-android/issues/46) | Conversation screen: streaming answers, stop, Markdown | M | 4 | `agent-ready` | E6-S2 |
| E6-S4 | [#47](https://github.com/EzraGubbay/quire-android/issues/47) | Citations: chips that open the source at the cited page | S | 4 | `agent-ready` | E6-S3, E5-S3 |
| E6-S5 | [#48](https://github.com/EzraGubbay/quire-android/issues/48) | Chat failures: error kinds, retry, offline | S | 4 | `agent-ready` | E6-S3 |
| E6-S6 | [#49](https://github.com/EzraGubbay/quire-android/issues/49) | Ask about this document: document-scoped chat from the viewer | S | 4 | `agent-ready` | E6-S4, E6-S5 |

## E7 — Background upload / sync ([#8](https://github.com/EzraGubbay/quire-android/issues/8))

**Goal.** The app keeps itself current in the background and can send files from the phone to a course without the user waiting on a screen. **The mobile async-upload user story lives here** (QMOB-E7-S3..S5).

**Epic e2e / integration tests (defined first).** `SyncJourneyTest` (QMOB-E7-S0) with the WorkManager test driver: periodic sync surfaces a new server file; a pinned course auto-downloads it. Upload journey cases are specified once the upload contract (QMOB-E1-S8) is fixed.

**Done when.** No `@Ignore("QMOB-E7")` remains; upload stories either shipped or explicitly deferred by the human.

**Out of scope.** Push-triggered sync (E8), editing remote content, conflict resolution (uploads are create-only).

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E7-S0 | [#50](https://github.com/EzraGubbay/quire-android/issues/50) | Define the E7 e2e journey first (sync): ignored specs + test-tag contract | S | 5 | `agent-ready` | E0-S3 |
| E7-S1 | [#51](https://github.com/EzraGubbay/quire-android/issues/51) | Background metadata sync (WorkManager periodic) | S | 5 | `agent-ready` | E7-S0, E4-S1, E3-S5 |
| E7-S2 | [#52](https://github.com/EzraGubbay/quire-android/issues/52) | Offline pinning: auto-download new and changed files for pinned courses | M | 5 | `agent-ready` | E7-S1, E5-S4 |
| E7-S3 | [#53](https://github.com/EzraGubbay/quire-android/issues/53) | Upload queue engine (background, resumable) | M | 5 | `needs-spec` | E1-S8, E7-S1 |
| E7-S4 | [#54](https://github.com/EzraGubbay/quire-android/issues/54) | Share to Quire: Android share target → course picker → enqueue | S | 5 | `needs-spec` | E7-S3 |
| E7-S5 | [#55](https://github.com/EzraGubbay/quire-android/issues/55) | Upload status: queue screen, progress notification, failure recovery | S | 5 | `needs-spec` | E7-S3 |

Open questions:
- E7-S3: Blocked on the upload contract (QMOB-E1-S8): single streamed request vs chunked, size limit, status model.
- E7-S3: Which file kinds are accepted from mobile (PDF, PPTX, Keynote, images)?
- E7-S3: Which classification fields are required at enqueue time (type, session)?
- E7-S4: Depends on the classification fields decided in QMOB-E1-S8.
- E7-S4: Multi-file share: one course/type for all, or per-file choice?
- E7-S4: Maximum size and behaviour on mobile data (ask / Wi-Fi only)?
- E7-S5: Depends on the status states exposed by QMOB-E1-S8.
- E7-S5: Where does the queue live in the navigation (Downloads tab section vs Settings)?

## E8 — Push notifications (Firebase) — deferred checkpoint ([#9](https://github.com/EzraGubbay/quire-android/issues/9))

**Goal.** Remote push notifications on Android (e.g. new course file, upload processed). **Deferred checkpoint: nothing in this epic blocks v1.0.0; do not start until the human confirms Firebase is set up and reopens Sprint 6.**

**Epic e2e / integration tests (defined first).** Defined after the spike (QMOB-E8-SP): instrumented tests that inject a fake FCM `RemoteMessage` and assert the notification + deep link; server integration test with a fake FCM transport.

**Done when.** v1.1.0 released; a real push received on the OnePlus 15R opens the right screen.

**Out of scope.** iOS push (out of scope entirely), marketing notifications, in-app inbox.

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E8-SP | [#61](https://github.com/EzraGubbay/quire-android/issues/61) | Spike: push architecture + event catalogue (FCM) | S | 6 | `agent-ready` | — |
| E8-S1 | [#62](https://github.com/EzraGubbay/quire-android/issues/62) | Firebase project + credentials (human setup) | XS | 6 | `human-only` | — |
| E8-S2 | [quire#41](https://github.com/EzraGubbay/quire/issues/41) | Server: device registration + FCM sender (apps/web) | M | 6 | `needs-spec` | E8-SP, E8-S1 |
| E8-S3 | [#63](https://github.com/EzraGubbay/quire-android/issues/63) | Android: FCM registration, notification channels, permission | S | 6 | `needs-spec` | E8-S2 |
| E8-S4 | [#64](https://github.com/EzraGubbay/quire-android/issues/64) | Notification deep links into course / file / thread | S | 6 | `needs-spec` | E8-S3 |
| E8-S5 | [#65](https://github.com/EzraGubbay/quire-android/issues/65) | Notification preferences | XS | 6 | `needs-spec` | E8-S3 |

Open questions:
- E8-S2: Event catalogue and payload shape — blocked by ADR 0002 (QMOB-E8-SP).
- E8-S2: Where the service-account credential is loaded from on the server (human decision).
- E8-S3: Blocked by ADR 0002 and by QMOB-E8-S1 (Firebase project).
- E8-S4: Blocked by ADR 0002 (event catalogue defines the targets).
- E8-S5: Blocked by ADR 0002 (categories; local vs server-side filtering).

## E9 — Release: signing, signed APK, GitHub Release, Obtainium ([#10](https://github.com/EzraGubbay/quire-android/issues/10))

**Goal.** A signed, minified APK published as a GitHub Release on every release tag, installable and updatable through Obtainium.

**Epic e2e / integration tests (defined first).** Minified-build smoke journey (launch → pair → courses → open PDF → chat stream) on the emulator; `scripts/check-release.sh <tag>` asserts the release assets; pre-release tag dry run.

**Done when.** v1.0.0 GitHub Release exists with exactly one APK + checksum; Obtainium installs and updates it on the OnePlus 15R.

**Out of scope.** Play Store, F-Droid, in-app updater, crash reporting.

| Story | Issue | Title | Size | Sprint | Status | Depends on |
|---|---|---|---|---|---|---|
| E9-S1 | [#56](https://github.com/EzraGubbay/quire-android/issues/56) | Release signing config (keystore from env, never committed) | S | 5 | `agent-ready` | E0-S5 |
| E9-S2 | [#57](https://github.com/EzraGubbay/quire-android/issues/57) | Release build hardening: R8 shrinking + minified smoke test | S | 5 | `agent-ready` | E9-S1, E6-S6 |
| E9-S3 | [#58](https://github.com/EzraGubbay/quire-android/issues/58) | Release workflow: tag → signed APK → GitHub Release | S | 5 | `human-only` | E9-S1, E9-S2 |
| E9-S4 | [#59](https://github.com/EzraGubbay/quire-android/issues/59) | Obtainium compatibility + install docs | S | 5 | `agent-ready` | E9-S3 |
| E9-S5 | [#60](https://github.com/EzraGubbay/quire-android/issues/60) | v1.0.0 release: on-device acceptance + CHANGELOG | XS | 5 | `human-only` | E9-S4, E7-S2 |

## iOS (after Android)

iOS mirrors Android epics E2–E9 (E1, the API layer, is shared; **no push epic**). Epic parent issues exist in
`EzraGubbay/quire-ios`, all `needs-spec`; stories are written at iOS kickoff, after Android v1.1.0 ships.

| Epic | Issue | Mirrors | Title | iOS sprint |
|---|---|---|---|---|
| iE0 | [quire-ios#1](https://github.com/EzraGubbay/quire-ios/issues/1) | E0 | Repo scaffold + CI/CD pipeline (Xcode, SwiftLint, XCTest, XCUITest) | 1 |
| iE2 | [quire-ios#2](https://github.com/EzraGubbay/quire-ios/issues/2) | E2 | App shell: navigation + iOS-native design system | 1 |
| iE3 | [quire-ios#3](https://github.com/EzraGubbay/quire-ios/issues/3) | E3 | Auth: device pairing against the mobile API | 1 |
| iE4 | [quire-ios#4](https://github.com/EzraGubbay/quire-ios/issues/4) | E4 | Course & file browser (reader) with type labels | 2 |
| iE5 | [quire-ios#5](https://github.com/EzraGubbay/quire-ios/issues/5) | E5 | Document viewer (PDFKit) + downloads | 2 |
| iE6 | [quire-ios#6](https://github.com/EzraGubbay/quire-ios/issues/6) | E6 | AI chat (streaming) | 3 |
| iE7 | [quire-ios#7](https://github.com/EzraGubbay/quire-ios/issues/7) | E7 | Background upload / sync | 4 |
| iE9 | [quire-ios#8](https://github.com/EzraGubbay/quire-ios/issues/8) | E9 | Release: signing + distribution | 4 |
