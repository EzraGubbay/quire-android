# Sprints and release checkpoints

**1 sprint = 1 release.** Each sprint is a GitHub milestone; it ends with one `dev-rc` → `main` release PR and one tag.
Only Sprint 1 is open. Later sprints exist as *closed* milestones marked PLANNED, with their issues already assigned;
the human reopens a milestone to start that sprint and closes it at the release.

No dates are set: add them on the milestones or with the Project's Iteration field (2-week iterations suggested).

## Sprint lifecycle

1. **Start:** reopen the sprint's milestone. In the Project, move its `agent-ready` stories to *Ready for Agent*. The epic's `S0` story (e2e journey first) goes first.
2. **During:** one story = one feature branch = one PR into `dev-rc`. Gate: `build`, `lint`, `unit-tests`. After each merge the `e2e` job runs on `dev-rc` as an early signal.
3. **Release checkpoint:** when the sprint's epics are done and `dev-rc` is shippable, open the release PR `dev-rc` → `main`, bumping `version.properties`. Gate: CI **and** `e2e` green, and no `@Ignore("QMOB…")` e2e spec left. Tick the checkpoint list below in the PR.
4. **Ship:** the human merges, tags `v<version>` on `main`, closes the milestone. From v1.0.0 the tag publishes a signed GitHub Release; before signing is configured the tag is a recorded checkpoint and the installable build is the `quire-debug-apk` artifact of the CI run.
5. **Carry-over:** unfinished stories move to the next milestone explicitly; a sprint never ships with a half-done epic's `@Ignore` in place — finish it or defer the epic.

Option (human decision): pull QMOB-E9-S1 and QMOB-E9-S3 forward into Sprint 2 to get signed pre-releases through Obtainium from v0.2.0 onward.

## Android sprints

| Sprint (milestone) | State | Epics | Release | Stories | Sizes (XS/S/M) |
|---|---|---|---|---|---|
| Sprint 1 — Foundations (v0.1.0) | **open** | E0, E1 | v0.1.0 | 14 | 2/10/2 |
| Sprint 2 — Shell & Sign-in (v0.2.0) | planned (closed) | E2, E3 | v0.2.0 | 13 | 3/9/1 |
| Sprint 3 — Browse & Read (v0.3.0) | planned (closed) | E4, E5 | v0.3.0 | 14 | 1/9/4 |
| Sprint 4 — AI Chat (v0.4.0) | planned (closed) | E6 | v0.4.0 | 7 | 0/5/2 |
| Sprint 5 — Sync & Release (v1.0.0) | planned (closed) | E1, E7, E9 | v1.0.0 | 12 | 1/8/3 |
| Sprint 6 — Push Notifications (v1.1.0) | planned (closed) | E8 | v1.1.0 | 6 | 2/3/1 |

Sprint 1 and the later API stories also have milestones in `EzraGubbay/quire` (E1 lives there): *QMOB Sprint 1 — Mobile API v1* (open); *QMOB Sprint 5 — Mobile uploads API* (planned); *QMOB Sprint 6 — Push sender* (planned).

## Release checkpoints (dev-rc → main)

### v0.1.0 — Sprint 1 — Foundations (v0.1.0)

- [ ] All four CI jobs (`build`, `lint`, `unit-tests`, `e2e`) run and pass on `dev-rc`.
- [ ] `dev-rc` and `main` rulesets active (QMOB-E0-S4).
- [ ] Mobile API v1 merged and deployed from `EzraGubbay/quire` (`e2e/mobile-api.spec.ts` green; only the upload step still fixme).
- [ ] Skeleton app launches on the emulator (`app-root`).
- [ ] Release PR `dev-rc` → `main` green: `build`, `lint`, `unit-tests`, `e2e`.

| Story | Issue | Title | Size | Status |
|---|---|---|---|---|
| E0-S1 | [#11](https://github.com/EzraGubbay/quire-android/issues/11) | Gradle scaffold: single-activity Kotlin + Compose app | S | `agent-ready` |
| E0-S2 | [#12](https://github.com/EzraGubbay/quire-android/issues/12) | Static analysis: ktlint + detekt + Android Lint | S | `agent-ready` |
| E0-S3 | [#13](https://github.com/EzraGubbay/quire-android/issues/13) | Instrumented test harness: Compose UI tests + MockWebServer on an emulator | S | `agent-ready` |
| E0-S4 | [#14](https://github.com/EzraGubbay/quire-android/issues/14) | Branch model + rulesets: dev-rc and main hard rules | XS | `human-only` |
| E0-S5 | [#15](https://github.com/EzraGubbay/quire-android/issues/15) | Versioning: versionName / versionCode from version.properties | XS | `agent-ready` |
| E1-S0 | [quire#31](https://github.com/EzraGubbay/quire/issues/31) | Define the mobile API integration suite first (Playwright API spec, fixme) | S | `agent-ready` |
| E1-S1 | [quire#32](https://github.com/EzraGubbay/quire/issues/32) | Contract: OpenAPI 3.1 for /api/mobile/v1 + error envelope + zod schemas | S | `agent-ready` |
| E1-S2 | [quire#33](https://github.com/EzraGubbay/quire/issues/33) | Mobile auth: per-device tokens + pairing-code exchange | M | `agent-ready` |
| E1-S3 | [quire#34](https://github.com/EzraGubbay/quire/issues/34) | Settings → Mobile devices: pairing code + QR, list and revoke devices | S | `agent-ready` |
| E1-S4 | [quire#35](https://github.com/EzraGubbay/quire/issues/35) | Courses, sessions and files endpoints (labelled JSON) | S | `agent-ready` |
| E1-S5 | [quire#36](https://github.com/EzraGubbay/quire/issues/36) | File content endpoint: streaming with Range and ETag | S | `agent-ready` |
| E1-S6 | [quire#37](https://github.com/EzraGubbay/quire/issues/37) | Notes read endpoints (Markdown, read-only) | S | `agent-ready` |
| E1-S7 | [quire#38](https://github.com/EzraGubbay/quire/issues/38) | Chat endpoints: threads, messages, NDJSON streaming answer | M | `agent-ready` |
| E1-S9 | [quire#40](https://github.com/EzraGubbay/quire/issues/40) | Mobile API docs + contract fixtures for the apps | S | `agent-ready` |

### v0.2.0 — Sprint 2 — Shell & Sign-in (v0.2.0)

- [ ] `ShellJourneyTest` and `AuthJourneyTest` fully un-ignored and green.
- [ ] On the phone: pair with the production server by QR, relaunch stays signed in.
- [ ] Design system goldens committed; contrast tests green.
- [ ] Release PR `dev-rc` → `main` green: `build`, `lint`, `unit-tests`, `e2e`.

| Story | Issue | Title | Size | Status |
|---|---|---|---|---|
| E2-S0 | [#16](https://github.com/EzraGubbay/quire-android/issues/16) | Define the E2 e2e journey first (shell): ignored specs + test-tag contract | XS | `agent-ready` |
| E2-S1 | [#17](https://github.com/EzraGubbay/quire-android/issues/17) | Design tokens + QuireTheme (color, typography, shape, spacing) | S | `agent-ready` |
| E2-S2 | [#18](https://github.com/EzraGubbay/quire-android/issues/18) | Navigation scaffold: bottom bar (Courses · Downloads · Chats) + Settings | S | `agent-ready` |
| E2-S3 | [#19](https://github.com/EzraGubbay/quire-android/issues/19) | Core component kit: top bar, course card, file row + type chip, state views | M | `agent-ready` |
| E2-S4 | [#20](https://github.com/EzraGubbay/quire-android/issues/20) | Screenshot regression tests for the design system (Roborazzi) | S | `agent-ready` |
| E2-S5 | [#21](https://github.com/EzraGubbay/quire-android/issues/21) | Settings screen: theme mode, device colors, about | S | `agent-ready` |
| E2-S6 | [#22](https://github.com/EzraGubbay/quire-android/issues/22) | App identity: adaptive icon, themed (monochrome) icon, splash screen | XS | `needs-spec` |
| E3-S0 | [#23](https://github.com/EzraGubbay/quire-android/issues/23) | Define the E3 e2e journey first (auth): ignored specs + test-tag contract | XS | `agent-ready` |
| E3-S1 | [#24](https://github.com/EzraGubbay/quire-android/issues/24) | Network core: API client, auth interceptor, error envelope → ApiResult | S | `agent-ready` |
| E3-S2 | [#25](https://github.com/EzraGubbay/quire-android/issues/25) | Secure session storage (Keystore-encrypted token) | S | `agent-ready` |
| E3-S3 | [#26](https://github.com/EzraGubbay/quire-android/issues/26) | Pairing screen: server URL + code entry | S | `agent-ready` |
| E3-S4 | [#27](https://github.com/EzraGubbay/quire-android/issues/27) | QR pairing + quire://pair deep link | S | `agent-ready` |
| E3-S5 | [#28](https://github.com/EzraGubbay/quire-android/issues/28) | Session lifecycle: expiry handling, sign-out, account section in Settings | S | `agent-ready` |

### v0.3.0 — Sprint 3 — Browse & Read (v0.3.0)

- [ ] `CoursesJourneyTest` and `ReaderJourneyTest` fully un-ignored and green (notes-reader cases only if QMOB-E5-S6 was specced and shipped).
- [ ] On the phone: browse a real course, open a 100+ page deck, download it, read it in airplane mode.
- [ ] ADR 0001 (Markdown + math) merged.
- [ ] Release PR `dev-rc` → `main` green: `build`, `lint`, `unit-tests`, `e2e`.

| Story | Issue | Title | Size | Status |
|---|---|---|---|---|
| E4-S0 | [#29](https://github.com/EzraGubbay/quire-android/issues/29) | Define the E4 e2e journey first (browse): ignored specs + test-tag contract | S | `agent-ready` |
| E4-S1 | [#30](https://github.com/EzraGubbay/quire-android/issues/30) | Offline-first data layer: Room cache + repositories for courses, sessions, files | M | `agent-ready` |
| E4-S2 | [#31](https://github.com/EzraGubbay/quire-android/issues/31) | Courses screen: semester-grouped list with refresh and states | S | `agent-ready` |
| E4-S3 | [#32](https://github.com/EzraGubbay/quire-android/issues/32) | Course screen: Sessions · Files · Notes tabs | S | `agent-ready` |
| E4-S4 | [#33](https://github.com/EzraGubbay/quire-android/issues/33) | Files tab: type labels, filter chips, sort, file info sheet | S | `agent-ready` |
| E4-S5 | [#34](https://github.com/EzraGubbay/quire-android/issues/34) | Search across courses and files (cached, offline) | S | `agent-ready` |
| E5-S0 | [#35](https://github.com/EzraGubbay/quire-android/issues/35) | Define the E5 e2e journey first (reader): ignored specs + test-tag contract | S | `agent-ready` |
| E5-S1 | [#36](https://github.com/EzraGubbay/quire-android/issues/36) | Download engine: resumable WorkManager downloads with Room state | M | `agent-ready` |
| E5-S2 | [#37](https://github.com/EzraGubbay/quire-android/issues/37) | PDF viewer: smooth paging, zoom, resume position | M | `agent-ready` |
| E5-S3 | [#38](https://github.com/EzraGubbay/quire-android/issues/38) | Open-file flow: stream-to-cache, download toggle, originals via open-with | S | `agent-ready` |
| E5-S4 | [#39](https://github.com/EzraGubbay/quire-android/issues/39) | Downloads tab: offline library, storage use, remove / retry, progress notification | S | `agent-ready` |
| E5-S5 | [#40](https://github.com/EzraGubbay/quire-android/issues/40) | Share / save a copy of a downloaded file | XS | `agent-ready` |
| E5-SP | [#41](https://github.com/EzraGubbay/quire-android/issues/41) | Spike: Markdown + LaTeX rendering approach for notes and chat answers | S | `agent-ready` |
| E5-S6 | [#42](https://github.com/EzraGubbay/quire-android/issues/42) | Notes reader (read-only Markdown with math) | M | `needs-spec` |

### v0.4.0 — Sprint 4 — AI Chat (v0.4.0)

- [x] `ChatJourneyTest` fully un-ignored and green. (65/65 e2e incl. 15 ChatJourneyTest — run 44743397434, `e4351e5`)
- [ ] On the phone: ask a question about a course and about an open document; answer streams; citation opens the page. _(human on-device acceptance)_
- [ ] Release PR `dev-rc` → `main` green: `build`, `lint`, `unit-tests`, `e2e`.

| Story | Issue | Title | Size | Status |
|---|---|---|---|---|
| E6-S0 | [#43](https://github.com/EzraGubbay/quire-android/issues/43) | Define the E6 e2e journey first (chat): ignored specs + test-tag contract | S | `agent-ready` |
| E6-S1 | [#44](https://github.com/EzraGubbay/quire-android/issues/44) | Chat data layer: threads/messages API + NDJSON stream → Flow<ChatEvent> | M | `agent-ready` |
| E6-S2 | [#45](https://github.com/EzraGubbay/quire-android/issues/45) | Thread lists: per-course chats and the global Chats tab | S | `agent-ready` |
| E6-S3 | [#46](https://github.com/EzraGubbay/quire-android/issues/46) | Conversation screen: streaming answers, stop, Markdown | M | `agent-ready` |
| E6-S4 | [#47](https://github.com/EzraGubbay/quire-android/issues/47) | Citations: chips that open the source at the cited page | S | `agent-ready` |
| E6-S5 | [#48](https://github.com/EzraGubbay/quire-android/issues/48) | Chat failures: error kinds, retry, offline | S | `agent-ready` |
| E6-S6 | [#49](https://github.com/EzraGubbay/quire-android/issues/49) | Ask about this document: document-scoped chat from the viewer | S | `agent-ready` |

### v1.0.0 — Sprint 5 — Sync & Release (v1.0.0)

- [ ] `SyncJourneyTest` green; upload stories shipped or explicitly deferred by the human.
- [ ] Signed APK published by the release workflow as a GitHub Release (one APK + checksum).
- [ ] Obtainium installs and updates the app on the OnePlus 15R (QMOB-E9-S5).
- [ ] Release PR `dev-rc` → `main` green: `build`, `lint`, `unit-tests`, `e2e`.

| Story | Issue | Title | Size | Status |
|---|---|---|---|---|
| E1-S8 | [quire#39](https://github.com/EzraGubbay/quire/issues/39) | Upload endpoint for mobile (async, resumable) | M | `needs-spec` |
| E7-S0 | [#50](https://github.com/EzraGubbay/quire-android/issues/50) | Define the E7 e2e journey first (sync): ignored specs + test-tag contract | S | `agent-ready` |
| E7-S1 | [#51](https://github.com/EzraGubbay/quire-android/issues/51) | Background metadata sync (WorkManager periodic) | S | `agent-ready` |
| E7-S2 | [#52](https://github.com/EzraGubbay/quire-android/issues/52) | Offline pinning: auto-download new and changed files for pinned courses | M | `agent-ready` |
| E7-S3 | [#53](https://github.com/EzraGubbay/quire-android/issues/53) | Upload queue engine (background, resumable) | M | `needs-spec` |
| E7-S4 | [#54](https://github.com/EzraGubbay/quire-android/issues/54) | Share to Quire: Android share target → course picker → enqueue | S | `needs-spec` |
| E7-S5 | [#55](https://github.com/EzraGubbay/quire-android/issues/55) | Upload status: queue screen, progress notification, failure recovery | S | `needs-spec` |
| E9-S1 | [#56](https://github.com/EzraGubbay/quire-android/issues/56) | Release signing config (keystore from env, never committed) | S | `agent-ready` |
| E9-S2 | [#57](https://github.com/EzraGubbay/quire-android/issues/57) | Release build hardening: R8 shrinking + minified smoke test | S | `agent-ready` |
| E9-S3 | [#58](https://github.com/EzraGubbay/quire-android/issues/58) | Release workflow: tag → signed APK → GitHub Release | S | `human-only` |
| E9-S4 | [#59](https://github.com/EzraGubbay/quire-android/issues/59) | Obtainium compatibility + install docs | S | `agent-ready` |
| E9-S5 | [#60](https://github.com/EzraGubbay/quire-android/issues/60) | v1.0.0 release: on-device acceptance + CHANGELOG | XS | `human-only` |

### v1.1.0 — Sprint 6 — Push Notifications (v1.1.0)

- [ ] Deferred checkpoint passed: Firebase set up by the human (QMOB-E8-S1), ADR 0002 merged, E8 stories specced.
- [ ] A real push arrives on the phone and opens the right screen.
- [ ] **Android is complete — iOS may start.**
- [ ] Release PR `dev-rc` → `main` green: `build`, `lint`, `unit-tests`, `e2e`.

| Story | Issue | Title | Size | Status |
|---|---|---|---|---|
| E8-SP | [#61](https://github.com/EzraGubbay/quire-android/issues/61) | Spike: push architecture + event catalogue (FCM) | S | `agent-ready` |
| E8-S1 | [#62](https://github.com/EzraGubbay/quire-android/issues/62) | Firebase project + credentials (human setup) | XS | `human-only` |
| E8-S2 | [quire#41](https://github.com/EzraGubbay/quire/issues/41) | Server: device registration + FCM sender (apps/web) | M | `needs-spec` |
| E8-S3 | [#63](https://github.com/EzraGubbay/quire-android/issues/63) | Android: FCM registration, notification channels, permission | S | `needs-spec` |
| E8-S4 | [#64](https://github.com/EzraGubbay/quire-android/issues/64) | Notification deep links into course / file / thread | S | `needs-spec` |
| E8-S5 | [#65](https://github.com/EzraGubbay/quire-android/issues/65) | Notification preferences | XS | `needs-spec` |

## Suggested order inside Sprint 1

1. Human: merge the setup PR, create `dev-rc`, apply rulesets (QMOB-E0-S4), add `CLAUDE_CODE_OAUTH_TOKEN`.
2. QMOB-E0-S1 (scaffold) → E0-S2, E0-S3, E0-S5 in parallel.
3. In `EzraGubbay/quire`, in parallel with the above: E1-S0 → E1-S1 → E1-S2 → (E1-S3, E1-S4, E1-S6, E1-S7 in parallel) → E1-S5 → E1-S9.
4. Release checkpoint v0.1.0.

## iOS sprints (planned — start after Android v1.1.0)

| Sprint (milestone in quire-ios) | Epics | Release |
|---|---|---|
| iOS Sprint 1 — Scaffold, Shell & Sign-in (v0.1.0) | iE0, iE2, iE3 | v0.1.0 |
| iOS Sprint 2 — Browse & Read (v0.2.0) | iE4, iE5 | v0.2.0 |
| iOS Sprint 3 — AI Chat (v0.3.0) | iE6 | v0.3.0 |
| iOS Sprint 4 — Sync & Release (v1.0.0) | iE7, iE9 | v1.0.0 |

iOS has no push sprint (push notifications are out of scope on iOS).
