# E8 — Push notifications (FCM registration · receiving · deep links · preferences): e2e contract

Epic: [QMOB-E8 (#9)](https://github.com/EzraGubbay/quire-android/issues/9) ·
Spec: [ADR 0003 — Push notifications](../adr/0003-push-notifications.md) (§6 is this journey) ·
Journey written first by [QMOB-E8-S3 (#63)](https://github.com/EzraGubbay/quire-android/issues/63).

This is the **contract** the push stories (#63–#65) build to. The instrumented journey
(`app/src/androidTest/java/com/ezragubbay/quire/e2e/PushJourneyTest.kt`) finds UI **by test tag only** and runs
against `MockWebServer` fixtures whose shapes mirror the `mobile-v1` push contract. Every case shipped Ignored with
reason `QMOB-E8`; a story removes that Ignore from the cases it satisfies. The epic is done when no such Ignore
remains in `PushJourneyTest` (a release PR into `main` fails while any remains).

> The literal Ignore annotation text is written **only** in the real annotations in `PushJourneyTest.kt`. Docs and
> KDoc describe the convention as "Ignored with reason QMOB-E8" so the release-gate grep over `app/src/androidTest`
> matches annotations only.

## No Firebase in the journey

There is no `google-services.json` in CI, so `BuildConfig.PUSH_CONFIGURED` is false and production Hilt would bind
the no-op token source. Instrumented tests replace `PushTokenModule` for **every** test class with
`testing/FakeFcmTokenSource` (`@TestInstallIn`):

- It starts **unavailable** (push off, exactly like a build without the file), so the other journeys never see push
  requests on their response queues.
- `PushJourneyTest` switches it on (`tokens.available = true`); it then hands out the fixed token `fake-fcm-token`
  and records `token`, `delete` and `autoInit=<bool>` calls.

A push is injected by building `RemoteMessage.Builder("quire@fcm.googleapis.com").setData(map).build()` and passing
`.data` to the app's `PushMessageHandler` — the same call `QuireMessagingService.onMessageReceived` makes. No FCM, no
Firebase initialisation. `POST_NOTIFICATIONS` is granted with `GrantPermissionRule`. Notifications are asserted with
`NotificationManager.activeNotifications` (channel id, tag) and opened with `notification.contentIntent.send()`.

The mock server answers **by method and path** (a `Dispatcher` in the test), not from a queue: the app's own
background calls (the app-start `PUT`, name lookups on receipt) must not take responses meant for other requests.

## The mobile API this talks to (ADR 0003 §3.1)

| Operation | Success | Notes |
|---|---|---|
| `PUT /push/registration` | `200` `{ registration, pushEnabled }` | Body `{ fcmToken, platform: "android", appVersion, categories }`. Sent on pairing, on every app start, on a new token, on a category change, and after a test push returns `unregistered`. |
| `DELETE /push/registration` | `204` | Sign-out, **before** `DELETE /auth/token`. |
| `POST /push/test` | `200` `{ eventId, outcome }` | `outcome`: `sent`, `unregistered`, `retryable`, `failed`. `400` no registration, `429` rate limited, `503 push_unavailable`. |
| `GET /courses/{course}` · `GET /courses/{course}/files/{fileId}` · `GET /courses/{course}/notes/{noteId}` | `200` | Name lookups on receipt (Room first). A miss falls back to generic text. |

## Payloads (FCM data messages, every value a string)

| `type` | Keys besides `v`, `type`, `eventId` | Channel | Tag | Tap opens |
|---|---|---|---|---|
| `files.added` | `course`, `courseId`, `fileId`, `count` | `push_files` | `files:{courseId}` | `count = 1`: viewer on `fileId`; else the course |
| `homework.due` | `course`, `courseId`, `assignmentId`, `number`, `dueAt`, `fileId`? | `push_deadlines` | `homework:{assignmentId}` | the sheet (`fileId`), else the course |
| `note.synthesized` | `course`, `courseId`, `noteId`, `outcome` | `push_notes` | `note:{noteId}` | `ready`: note reader; `failed`: the course |
| `push.test` | — | `push_system` | `test` | Courses |

An unknown `v` or `type`, or a missing id, is dropped: no notification.

The tap intent is explicit to `MainActivity`, action `com.ezragubbay.quire.action.OPEN_FROM_PUSH`, extras `type`
(the event type), `course`, `fileId`, `noteId`. Until QMOB-E8-S4 handles it, a tap opens the app at its start screen.

## Test-tag contract

### Permission card — placed by QMOB-E8-S3 (#63)

Shown once on Courses after the first successful pairing, on Android 13+, while `POST_NOTIFICATIONS` is not granted.

| Tag | Meaning |
|---|---|
| `push-permission-card` | The "Get notified about new course files and homework deadlines" card. |
| `action-notifications-allow` | Asks for the permission (or opens the app's notification settings once the dialog can no longer show). |
| `action-notifications-not-now` | Dismisses the card for good. |

### Deep links — placed by QMOB-E8-S4 (#64)

| Tag | Meaning |
|---|---|
| `push-target-missing` | Snackbar shown when the tapped file/note/course no longer exists ("That file is no longer available"). |

Reused: `screen-courses`, `screen-course` (E4), `screen-pdf-viewer`, `screen-note-reader` (E5).

### Preferences — placed by QMOB-E8-S5 (#65) (draft; settled by that story)

| Tag | Meaning |
|---|---|
| `settings-notifications` | Settings → Notifications section. |
| `setting-push-files` / `setting-push-deadlines` / `setting-push-notes` | Category switches (default on). |
| `action-send-test-notification` | "Send test notification". |
| `push-test-result` | The test's outcome (sent / not set up on the server / no registration / rate limited). |

Reused: `action-settings`, `screen-settings` (E2), `action-sign-out`, `screen-pair`, `field-server-url`,
`field-pair-code`, `action-pair` (E3).

## Cases → owning story

| `PushJourneyTest` case | Un-ignored by |
|---|---|
| `registration_afterPairing_putsTokenAndCategories` | QMOB-E8-S3 (#63) |
| `filesAdded_single_postsNotificationOnFilesChannel` | QMOB-E8-S3 (#63) |
| `unknownType_postsNothing` | QMOB-E8-S3 (#63) |
| `signOut_deletesRegistrationThenRevokes` | QMOB-E8-S3 (#63) |
| `filesAdded_single_tapOpensViewer_backReturnsToCourse` | QMOB-E8-S4 (#64) |
| `filesAdded_many_tapOpensCourse` | QMOB-E8-S4 (#64) |
| `homeworkDue_tapOpensSheet` | QMOB-E8-S4 (#64) |
| `noteSynthesized_ready_tapOpensNoteReader` | QMOB-E8-S4 (#64) |
| `deletedFile_tapFallsBackToCourseWithMessage` | QMOB-E8-S4 (#64) |
| `categoryOff_putsCategories_andSuppressesNotification` | QMOB-E8-S5 (#65) |
| `sendTest_postsTestNotification` | QMOB-E8-S5 (#65) |

The S4 and S5 bodies are drafts: the owning story may adjust fixtures and routes (for example, serving the PDF for
the push's `fileId`) when it un-ignores them, but keeps the case names.

## Fixtures

Contract fixtures (`app/src/test/resources/contract/mobile-v1/`, used by the JVM tests):

| Fixture | Source |
|---|---|
| `putPushRegistration.request.json`, `putPushRegistration.200.json` | ADR 0003 §3.1 example |
| `getPushRegistration.200.json` | Same registration as the `PUT` (the ADR gives only the shape) |
| `sendTestPush.200.json` | ADR 0003 §3.1 (`outcome: sent`) |
| `error.push_unavailable.json` | ADR 0003 §3.1 code `push_unavailable` (message wording provisional) |
| `push.files.added.json` | ADR 0003 §2 FCM request `data` map, verbatim |
| `push.homework.due.json`, `push.note.synthesized.json`, `push.test.json` | ADR 0003 §1 catalogue keys, example ids |

They follow the server's fixture format (two-space JSON, trailing newline). The server's generated fixtures
(quire#41, from `events.ts` and the OpenAPI examples) were not published when this story landed, so these were written
from the ADR; when quire#41 merges, re-vendor them from `docs/api/fixtures/mobile-v1/` in `EzraGubbay/quire`.

Journey fixtures (`app/src/androidTest/assets/fixtures/`, byte-identical copies under journey names):

| Fixture | Mirrors | Used by |
|---|---|---|
| `push-registration.json` | `putPushRegistration.200.json` | every `PUT /push/registration` |
| `push-test-sent.json` | `sendTestPush.200.json` | `POST /push/test` |
| `push-files-added.json` | `push.files.added.json` | files cases, unknown type (with `type` replaced) |
| `push-homework-due.json` | `push.homework.due.json` | homework tap |
| `push-note-ready.json` | `push.note.synthesized.json` | note tap |
| `push-test.json` | `push.test.json` | send test |

Reused: `pair-success.json`, `me.json` (E3); `courses-list.json`, `course-detail.json`, `course-sessions.json`,
`course-files.json`, `course-notes.json`, `error-not-found.json` (E4); `reader-note.json` (E5).
