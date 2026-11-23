# ADR 0003: Push notifications (FCM)

Status: accepted (QMOB-E8-SP, #61)

> Naming note: #61, `docs/BACKLOG.md` and the E8 story drafts call this spike's output "ADR 0002". It ships as
> **0003** because `0002` is already the Markdown + LaTeX ADR.

## Context

Epic E8 adds remote push to the Android app (v1.1.0). The pieces that already exist:

- **Firebase** project `quire-push-a8d8c2` with Android app `com.ezragubbay.quire` (QMOB-E8-S1, done 2026-11-23).
  `google-services.json` reaches Android CI only as the Actions secret `GOOGLE_SERVICES_JSON_BASE64`; the server's
  FCM service-account JSON reaches `EzraGubbay/quire` only as the Actions secret `FCM_SERVICE_ACCOUNT_JSON_BASE64`.
- **Device identity**: every paired phone has a row in `mobile_tokens` (`id`, `owner_login`, `device_name`, hashed
  token, soft `revoked_at`), minted by `POST /api/mobile/v1/auth/pair` and revoked by `DELETE /auth/token` or from
  web Settings → Mobile devices (`apps/web/src/lib/mobile/auth.ts`, `devices.ts`). Identity is `owner_login`, so
  nothing assumes a single user.
- **Production** is one Next.js process (`apps/web`) in Docker Compose on researchpi, deployed by
  `.github/workflows/deploy.yml` on a self-hosted runner; all other server secrets already live in `/srv/quire/.env`.
  `apps/web/src/instrumentation.ts` runs once at boot (migrations, stale-job cleanup). There is no job queue or cron.
- **App**: Hilt, Navigation Compose with type-safe routes (`navigation/BrowseRoutes.kt`: `CourseRoute(slug)`,
  `ViewerRoute(courseSlug, fileId, page?)`, `NoteReaderRoute(courseSlug, noteId)`, `ConversationRoute(...)`), the
  shell's nested `NavHost` in `AppShell`, `POST_NOTIFICATIONS` already declared and requested in context by the E7
  pinned-download toggle (`feature/settings/SyncSection.kt`), one existing channel `sync`
  (`sync/PinnedDownloadNotifier.kt`), WorkManager metadata sync (`sync/SyncWorker.kt`).

Server events that exist today and could be worth a phone notification (surveyed in `apps/web/src`):

| Candidate | Where it happens | Verdict |
|---|---|---|
| New course file pulled from Lemida | `lib/lemida/import.ts` `importLemidaFile()` (via `POST /api/projects/[slug]/lemida/import`) | **v1** — the main reason to look at the phone |
| Homework due soon | `assignments.due_at` (set by hand or parsed by the Lemida import); `lib/homework.ts` `dueSoonAcrossCourses()` | **v1** — needs a small scheduler; high value |
| Synthesized study note finished / failed | `lib/ai/synthesis.ts` `runSynthesis()` (minutes-long background model call, started on the web) | **v1** — the note is readable on the phone |
| Weekly / tri-weekly practice test generated | `lib/ai/tests.ts` (user-triggered on the web) | Out: tests are web-only (AGENTS.md scope); Lemida-imported practice exams are already covered by "new file" |
| Long AI chat answer finished | `lib/chat-turn.ts` | Out: the turn is aborted with the request (`signal: req.signal`), so there is no "finished while you were away" |
| Upload processed / failed | `lib/mobile/uploads.ts` | Out: uploads from the phone are out of scope |
| Lemida file needs review | `lemida_files.status = 'needs_review'` | Out: can only be resolved on the web; the file still notifies as "new file" |

## Decision

### 1. Event catalogue (v1)

Four event types. Each maps to one **category** (a user-facing toggle, server-filtered) and one Android
**notification channel**. Payloads carry ids only (§2); the app fetches titles on receipt.

| Type | Category → channel (importance) | Server trigger | Data (besides `v`, `type`, `eventId`) | Title / body (rendered by the app) | Tap opens | FCM priority / TTL | Dedupe key |
|---|---|---|---|---|---|---|---|
| `files.added` | `files` → `push_files` (default) | `lib/lemida/import.ts` `importLemidaFile()`, after `recordFile()` when the result is **not** a duplicate and status is `imported` or `needs_review`. Coalesced per course (below). | `course` (slug), `courseId`, `fileId` (newest in the batch), `count` | Title: course name. Body: `count = 1` → "New {type label}: {file title}"; `count > 1` → "{count} new files" | `count = 1`: viewer on `fileId`; `count > 1`: the course | `NORMAL` / 7 days | `files.added:{lemidaFileId}` |
| `homework.due` | `deadlines` → `push_deadlines` (high) | New scheduler tick (`lib/mobile/push/scheduler.ts` `scanDeadlines()`, every 10 min): assignments with `due_at` in `(now, now + 24 h]` and status `not_started` or `in_progress` | `course`, `courseId`, `assignmentId`, `number`, `dueAt` (ISO-8601), `fileId` (the sheet document, omitted when none) | Title: "Homework {number} due {today/tomorrow HH:mm}" (device time zone). Body: course name, plus the sheet title when fetched | `fileId` present: viewer on the sheet; else the course | `HIGH` / until `dueAt` (max 24 h) | `homework.due:{assignmentId}:{dueAt epoch s}` (a moved deadline re-arms) |
| `note.synthesized` | `notes` → `push_notes` (default) | `lib/ai/synthesis.ts` `runSynthesis()`: after `updateNote(... status: 'ready')` (`outcome=ready`) and inside `fail()` (`outcome=failed`) | `course`, `courseId`, `noteId`, `outcome` (`ready`\|`failed`) | ready: "Study note ready" / note title. failed: "Note synthesis failed" / "Open Quire on the web to retry." | ready: note reader; failed: the course | `NORMAL` / 24 h | `note.synthesized:{noteId}:{outcome}:{note updatedAt ms}` (each run is distinct) |
| `push.test` | none (always on) → `push_system` (low) | `POST /api/mobile/v1/push/test` from the app's Settings (§3); calling device only | — | "Quire notifications work" / "Test sent from your server." | Courses (home) | `HIGH` / 5 min | none (random `eventId`) |

"Tap opens" always builds a real back stack: Courses → course → target (§4.5).

**Coalescing `files.added`.** The Lemida agent imports a course's new files one request at a time, and a newly
mapped course can bring dozens. Each import enqueues one outbox row with `group_key = files:{courseId}` and
`not_before = now + 90 s`; enqueueing pushes the group's pending rows' `not_before` to `now + 90 s`, capped at the
oldest row's `created_at + 10 min`. When due, the dispatcher sends **one** `files.added` per course and device with
`count` = rows in the group and `fileId` = the newest, and marks all of them sent. So: a single file notifies ~90 s
after import; a burst becomes one "N new files" notification.

**Explicitly not in v1:** chat-answer-ready (needs server-side turns that survive disconnects), practice-test
generated, upload processed, Lemida-agent-offline alerts, notification history/inbox, per-course mute, quiet hours.

### 2. Payload: data-only messages

Every push is an FCM **data message** (no `notification` block); the app builds the notification itself.

- A notification message is drawn by the system tray when the app is in the background and never reaches app
  code. We would lose: our channels (it can only name one), the in-app category check, the explicit deep-link
  `PendingIntent` with a back stack, fetching fresh titles (payloads are ids only), plural/locale/RTL formatting.
- Ids only, as #61 requires: course slug/uuid, file/note/assignment uuids, counts, timestamps. No titles, course
  names or note content cross FCM. The app resolves names on receipt (Room cache first, then
  `GET /courses/{course}/files/{fileId}` or `/notes/{noteId}` with a 5 s timeout) and falls back to generic text
  ("New file in {cached course name or 'a course'}") when offline.
- Wire shape: FCM `data` is `Map<String, String>`, so every value is a string. Common keys: `v` (payload version,
  `"1"`), `type`, `eventId` (uuid). Unknown `type` or `v` → the app drops the message (logged), so the server can add
  events without breaking v1.1 clients.
- **Priority.** `HIGH` only where timing matters and a notification always follows (`homework.due`, `push.test`);
  `NORMAL` otherwise (delivered at the next Doze maintenance window, which is fine for files and notes). Because the
  server filters by category (§3.4), every high-priority message the app receives produces a visible
  notification, so FCM does not deprioritise the app.
- **TTL** per the table (`android.ttl`). An outbox row whose TTL lapsed before it could be sent is dropped.
- **Collapse keys: not used**, except `push.test` (`collapse_key: "push.test"`). Collapsing data messages would
  drop distinct events stored for an offline phone; coalescing already happens server-side. Client-side, the
  notification **tag** replaces duplicates: `files.added` → `eventId`, `homework.due` → `homework:{assignmentId}`,
  `note.synthesized` → `note:{noteId}`, `push.test` → `test`. A redelivered message (same `eventId`) therefore
  replaces rather than duplicates.
- If FCM reports dropped pending messages (`onDeletedMessages`), the app enqueues a one-time metadata sync.

Example FCM HTTP v1 request body (sent per registration):

```json
{
  "message": {
    "token": "<fcm registration token>",
    "data": {
      "v": "1",
      "type": "files.added",
      "eventId": "7a1c2f0e-5b7d-4c2a-9a51-0f3e6d1b2c44",
      "course": "signals-and-systems",
      "courseId": "3f0b1c7e-2a7d-4d8e-9b1a-6c5e4f3a2b10",
      "fileId": "9c4e2b1a-7d3f-4a6b-8e2c-1f0a9b8c7d6e",
      "count": "1"
    },
    "android": { "priority": "NORMAL", "ttl": "604800s" }
  }
}
```

### 3. Server (EzraGubbay/quire, `apps/web`)

#### 3.1 API additions under `/api/mobile/v1` (additive; device bearer token like every other v1 route)

One registration per paired device: the device is identified by the calling `mobile_tokens` row, never by a client
supplied id.

**`PUT /push/registration`** — create or refresh this device's FCM token and categories. Idempotent: the same body
always yields the same state and `200`.

```json
// request
{ "fcmToken": "<fcm registration token>", "platform": "android", "appVersion": "1.1.0",
  "categories": ["files", "deadlines", "notes"] }
// 200
{ "registration": { "deviceId": "<mobile_tokens.id>", "platform": "android", "appVersion": "1.1.0",
    "categories": ["files", "deadlines", "notes"],
    "createdAt": "2026-11-24T09:00:00.000Z", "updatedAt": "2026-11-24T09:00:00.000Z" },
  "pushEnabled": true }
```

- `fcmToken` string 1–4096, required. `platform` enum `["android"]`, required. `appVersion` optional, ≤ 32 chars.
  `categories` required array (may be empty) of `files` | `deadlines` | `notes`; duplicates collapse, **unknown
  values are ignored** and left out of the response (forward compatible with newer apps). Malformed → `400
  invalid_request`.
- The response never echoes `fcmToken`. Logs never contain it (log the registration id).
- If the same `fcmToken` is registered under another device row (re-pairing on the same phone), that row is
  deleted: an FCM token belongs to exactly one device.
- `pushEnabled` says whether this server can send at all (credential present and valid, §5). Registrations are
  stored either way, so pushes start as soon as the credential appears.

**`GET /push/registration`** → `200 { "registration": { … } | null, "pushEnabled": bool }`.

**`DELETE /push/registration`** → `204`, also when nothing was registered.

**`POST /push/test`** (no body) → `202 { "eventId": "<uuid>" }`; sends `push.test` to the calling device
immediately (bypasses categories and coalescing). `400 invalid_request` when the device has no registration;
`429 rate_limited` above 1 per 30 s per device (existing `lib/mobile/rate-limit.ts`); **`503 push_unavailable`**
(new error code, only returned by this endpoint) when push is disabled on the server.

OpenAPI (`docs/api/mobile-v1.openapi.yaml`), zod schemas (`lib/mobile/schema.ts`), `docs/mobile-api.md` and the
generated fixtures gain these operations (`putPushRegistration.request.json`, `putPushRegistration.200.json`,
`getPushRegistration.200.json`, `sendTestPush.202.json`, `error.push_unavailable.json`) **plus one fixture per push
payload** (`push.files.added.json`, `push.homework.due.json`, `push.note.synthesized.json`, `push.test.json`, the
`data` map exactly as sent) so the app's contract tests decode what the server emits. `GET /me` `apiVersion` bumps
minor.

#### 3.2 Schema (one Drizzle migration)

```text
mobile_push_registrations
  id             uuid pk default random
  token_id       uuid not null unique  → mobile_tokens.id on delete cascade
  fcm_token      text not null unique
  platform       text not null            -- 'android'
  app_version    text
  categories     text[] not null default '{}'
  created_at, updated_at  timestamptz not null default now()
  last_sent_at   timestamptz
  last_error     text                     -- last non-retryable send error, for the web devices list later

mobile_push_outbox                        -- one row per (event, registration)
  id             uuid pk default random
  registration_id uuid not null → mobile_push_registrations.id on delete cascade
  type           text not null            -- 'files.added' | 'homework.due' | 'note.synthesized' | 'push.test'
  dedupe_key     text not null
  group_key      text                     -- 'files:{courseId}' for coalescing; null otherwise
  data           jsonb not null           -- the FCM data map (string values)
  priority       text not null            -- 'HIGH' | 'NORMAL'
  expires_at     timestamptz not null     -- created_at + TTL; past it the row is dropped, not sent
  not_before     timestamptz not null default now()
  attempts       int not null default 0
  created_at     timestamptz not null default now()
  sent_at        timestamptz
  dead_at        timestamptz              -- gave up (expired, unregistered, attempts exhausted)
  last_error     text
  unique (registration_id, dedupe_key)
  index (sent_at, dead_at, not_before)
```

**Revocation deletes push state.** `mobile_tokens` revocation is a soft `revoked_at` update, so the FK cascade does
not fire on revoke. `dbStore.revoke()` in `lib/mobile/auth.ts` therefore also deletes the device's registration
(same transaction; outbox rows cascade). This covers `DELETE /auth/token` and web Settings → Mobile devices →
Revoke. The dispatcher additionally only sends to registrations whose token has `revoked_at IS NULL`.

#### 3.3 Sender module — `apps/web/src/lib/mobile/push/`

| File | Responsibility |
|---|---|
| `config.ts` | `pushConfig()`: memoised. Reads `FCM_SERVICE_ACCOUNT_JSON_BASE64`, decodes, validates `type = service_account`, `project_id`, `client_email`, `private_key`. Returns `{ enabled: true, projectId, clientEmail, privateKey }` or `{ enabled: false, reason }`, logging **once** `[push] disabled: <reason>` (never the value). `PUSH_TRANSPORT=fake` → enabled with the fake transport (dev, CI e2e). |
| `transport.ts` | `interface PushTransport { send(m: { token, data, priority, ttlSeconds, collapseKey? }): Promise<SendResult> }`, `SendResult = { ok: true } \| { ok: false, kind: 'unregistered' \| 'retryable' \| 'fatal', retryAfterMs?, error }`. `fcmTransport(config, fetch = globalThis.fetch)`: OAuth2 access token from a self-signed RS256 JWT (`node:crypto`, scope `https://www.googleapis.com/auth/firebase.messaging`, exchanged at `https://oauth2.googleapis.com/token`, cached until 5 min before expiry), then `POST https://fcm.googleapis.com/v1/projects/{projectId}/messages:send`. No new dependency. `fakeTransport()` records messages and can be scripted per token (tests); under `PUSH_TRANSPORT=fake` it logs `[push:fake] sent type=<type> registration=<id>`. |
| `events.ts` | Pure builders, one per type: `filesAdded()`, `homeworkDue()`, `noteSynthesized()`, `pushTest()` → `{ type, category, dedupeKey, groupKey?, data, priority, ttlSeconds }`. zod schema per payload; fixture examples come from here. |
| `outbox.ts` | `enqueuePush(audience, event)`: no-op when push is disabled; inserts one row per matching registration (category enabled, token active) with `ON CONFLICT (registration_id, dedupe_key) DO NOTHING`; for `group_key` rows, slides the group's `not_before` (§1). Never throws into the caller: trigger sites wrap it so an import or synthesis can never fail because of push. |
| `dispatcher.ts` | `dispatchDue(now, deps)`: due rows (`sent_at IS NULL AND dead_at IS NULL AND not_before <= now`, limit 100); expired → `dead_at`, `last_error = 'expired'`; group rows merged per (registration, group) into one message; send; outcomes per §3.4. Re-entrancy guard (one run at a time). |
| `scheduler.ts` | `startPushLoop()`, called from `instrumentation.ts` after migrations (nodejs runtime only; idempotent across dev hot-reloads via a `globalThis` flag): every 30 s `dispatchDue`; every 10 min `scanDeadlines`; daily, delete outbox rows sent or dead more than 30 days ago. |
| `audience.ts` | `audienceForCourse(projectId)`: the owner logins whose devices may hear about a course. Projects have no owner column today, so it returns every owner with an active registration; the single seam to change when courses gain owners. `push.test` targets the calling device directly. |
| `registrations.ts` | `RegistrationStore` seam (SQL `dbRegistrations` + in-memory for tests, like `AuthStore`): upsert, get, delete, delete-by-token-id. |

#### 3.4 Delivery outcomes and dedupe

- **Dedupe**: at enqueue via `unique (registration_id, dedupe_key)` (the 10-minute deadline scan and a re-run
  import cannot double-notify); on the phone via the notification tag (§2).
- **Server-side category filter**: rows are only created for registrations whose `categories` contain the event's
  category, so a disabled category never wakes the phone.
- `200` → `sent_at = now`, registration `last_sent_at`.
- `404 UNREGISTERED`, `403 SENDER_ID_MISMATCH`, or `400 INVALID_ARGUMENT` whose field violation names
  `message.token` → **delete the registration** (its pending rows cascade), log `[push] pruned registration <id>`.
- `429 QUOTA_EXCEEDED`, `500 INTERNAL`, `503 UNAVAILABLE`, network errors → `attempts + 1`,
  `not_before = now + max(Retry-After, min(30 s · 2^attempts, 30 min))`; after 6 attempts → `dead_at`.
- `401` (access token rejected) → drop the cached access token and retry once in the same run; if it fails again,
  treat as retryable and log `[push] FCM auth failed` at most once an hour.
- Any other `4xx` → `dead_at` with `last_error` (a payload bug; retrying cannot help).
- FCM unavailable for a long time therefore costs nothing but bounded retries; events older than their TTL are
  dropped rather than delivered late.

### 4. Android

#### 4.1 Build without `google-services.json`

`google-services.json` is never committed (CI's `detect` secret guard fails the build if it is tracked), so there is
no placeholder file. Instead:

- The Google Services Gradle plugin is declared in the catalog and the root build with `apply false`, and the app
  module applies it **only when `app/google-services.json` exists**. The app module also sets
  `buildConfigField("boolean", "PUSH_CONFIGURED", "<file exists>")`.
- Dependencies are always present (Firebase BoM + `firebase-messaging`, `kotlinx-coroutines-play-services`); no
  Analytics.
- `AndroidManifest.xml` sets `firebase_messaging_auto_init_enabled = false`, so no token exists until the user is
  signed in (§4.2).
- All Firebase calls sit behind `push/FcmTokenSource` (`suspend fun token(): String?`, `suspend fun delete()`).
  Hilt binds `FirebaseTokenSource` when `BuildConfig.PUSH_CONFIGURED`, otherwise `NoopTokenSource` (returns null),
  so a build without the file never touches `FirebaseMessaging` and Settings says push is unavailable in this
  build. Unit and instrumented tests bind a fake.
- Local builds, PR builds and the `build` / `unit-tests` / `e2e` CI jobs run **without** the file: push disabled,
  everything compiles and passes. `release.yml` decodes `GOOGLE_SERVICES_JSON_BASE64` to `app/google-services.json`
  (umask 077, deleted after the build) and **fails the release if the secret is missing**, so a v1.1.0+ APK always
  has push. For a real-push check, the human drops the file into `app/` locally (git-ignored).

#### 4.2 Token lifecycle — `push/PushRegistrar`

- `sync()`: if not signed in or `!PUSH_CONFIGURED` → no-op. Otherwise enable FCM auto-init, get the token, and
  `PUT /push/registration` with the current categories, **only if** the fingerprint (hash of server URL + device id
  + token + categories) differs from the last successful one stored in DataStore, or that success is older than
  7 days. Failures are retried by `PushRegistrationWorker` (one-time unique work `push-registration`, network
  constraint, exponential backoff).
- Called on: pairing success (`AppViewModel.onSignedIn`), app start (alongside `SyncCoordinator.start()`),
  `FirebaseMessagingService.onNewToken` (enqueues the worker; the service has no UI scope), and category changes.
- Sign-out (`AppViewModel.signOut`): best-effort `DELETE /push/registration` (inside the existing revoke timeout)
  → `DELETE /auth/token` (server deletes the registration anyway) → `FcmTokenSource.delete()` (invalidates the
  token at FCM, so a server that missed both deletes gets `UNREGISTERED` and prunes it) → disable auto-init → clear
  the fingerprint. Session expiry (401, e.g. revoked from the web): the server already deleted the registration;
  the app runs the local half (delete token, clear fingerprint).
- Re-pairing (same or another server) runs `sync()` with the new device token; the server moves an existing FCM
  token to the new device row (§3.1).

#### 4.3 Receiving — `push/QuireMessagingService` + `push/PushMessageHandler`

- `QuireMessagingService : FirebaseMessagingService` (manifest: `exported="false"`,
  `com.google.firebase.MESSAGING_EVENT`) is a thin adapter: `onMessageReceived(m)` →
  `handler.handle(m.data)`; `onNewToken` → enqueue registration; `onDeletedMessages` → one-time metadata sync.
- `PushMessageHandler` (Hilt singleton, the unit under test) parses the map into a sealed `PushEvent`
  (`PushPayloadParser`, pure; unknown `v`/`type` or missing ids → drop), checks the local category toggle (defence
  in depth for a server that is behind), resolves display names (Room → API, 5 s), builds the notification
  (`PushNotificationFactory`, pure mapping event → channel, tag, title/body, target) and posts it if notifications
  are enabled and permitted. `files.added` also enqueues a one-time expedited metadata sync, so pinned courses
  auto-download the new file (E7's "push-triggered sync").
- Small icon: a monochrome Quire glyph (not the stock download icon).

#### 4.4 Channels and permission

- Channels, created idempotently at app start (`push/PushChannels.ensure()` from `QuireApplication`):
  `push_files` "New course files" (default), `push_deadlines` "Homework deadlines" (high), `push_notes`
  "Study notes" (default), `push_system` "Quire" (low). The E7 `sync` channel is unchanged.
- `POST_NOTIFICATIONS` (Android 13+; `minSdk 29` devices below 33 need nothing): never at launch. Asked (a) once,
  after the first successful pairing, from a dismissible card on the Courses screen ("Get notified about new
  course files and homework deadlines" — Allow / Not now; "asked" flag in DataStore), and (b) when the user turns a
  category on in Settings without the permission. Once the system will no longer show the dialog, the action opens
  the app's system notification settings (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
- Permission denied or app notifications off: the app registers with `categories: []`, so the server stops
  sending; restored → re-`sync()` on the next app start/resume.

#### 4.5 Deep links

- The notification's `PendingIntent` is an **explicit** intent to `MainActivity` (action
  `com.ezragubbay.quire.action.OPEN_FROM_PUSH`, extras `type`, `course`, `fileId`, `noteId`), not a URI deep link,
  so no new intent filter is added. `MainActivity` is already exported (launcher), so another app could send the
  same extras; they can only pick a screen inside the signed-in user's own data and go through the resolver below,
  so this grants nothing. Flags: `NEW_TASK | CLEAR_TOP | SINGLE_TOP`, `FLAG_IMMUTABLE`; request code from `eventId`.
- `PushTarget.fromIntent(intent)` (pure) → `Course(slug)`, `File(slug, fileId)`, `Note(slug, noteId)`, `Home`.
  `MainActivity` hands it to `AppViewModel.openFromPush()` in `onCreate` and `onNewIntent` (cold and warm start).
  The pending target is a `StateFlow` that `QuireApp` passes into `AppShell`, which consumes it once composed:
  pop to `CoursesRoute`, then navigate `CourseRoute(slug)` and, for files/notes, `ViewerRoute(slug, fileId)` /
  `NoteReaderRoute(slug, noteId)`. Back therefore walks target → course → Courses. `PushTarget.backStack()` is
  pure and unit-tested.
- Signed out: the target is dropped (the Pair screen shows).
- Stale target: before navigating, `PushTargetResolver` checks the course and file/note
  (`GET /courses/{course}`, `/files/{fileId}` or `/notes/{noteId}`, 5 s). `404` file/note → open the course with a
  snackbar "That file is no longer available" / "That note is no longer available"; `404` course → Courses with
  "That course is no longer available". Offline or timeout → navigate anyway (cached/offline content may exist).
- Threads (`ConversationRoute`): no v1 event targets a thread, so none is wired.

#### 4.6 Preferences — server-side filtering, in-app toggles

Settings → Notifications (new section) shows one switch per category (`files`, `deadlines`, `notes`, default all
on), each with a link to its system channel settings, plus "Send test notification". The switches are stored in
DataStore and **mirrored to the server** as `categories` in `PUT /push/registration`; the server filters (§3.4) and
the app re-checks on receipt. Why server-side: a disabled category then costs no wake-ups, and every high-priority
message the app gets ends in a visible notification (FCM deprioritises apps that receive high-priority messages
without showing one). The system channel switch still works independently; when a channel is blocked the in-app row
says "Blocked in system settings".

### 5. Secrets and deploy

- **Server** (`EzraGubbay/quire`): `deploy.yml`'s `deploy` job (self-hosted runner on researchpi, `production`
  environment) gains a step **before** "Pull, migrate, restart" that maps `secrets.FCM_SERVICE_ACCOUNT_JSON_BASE64`
  into the step's env and upserts the single line `FCM_SERVICE_ACCOUNT_JSON_BASE64=<base64>` in
  `/srv/quire/.env` (rewrite via `grep -v` + bash builtin `printf` into a temp file created under `umask 077`, then
  `cat` over `.env` so its mode and owner stay; no `set -x`; the secret never appears in a command line). Empty or
  missing secret → the line is removed and the step logs "push disabled: no FCM credential". `infra/compose.yml`
  passes `FCM_SERVICE_ACCOUNT_JSON_BASE64: ${FCM_SERVICE_ACCOUNT_JSON_BASE64:-}` to `app`;
  `infra/.env.example` documents the key (empty). Nobody edits the Pi by hand.
- Why `.env` and not a mounted file: every other server secret already lives in `/srv/quire/.env`, and a bind
  mounted file would need ownership changes for the container's uid 1001 (the deploy user cannot `sudo` on
  researchpi). The base64 form keeps it one line. It is not in backups (the backup container only mounts the files
  volume and Postgres).
- **Degradation**: no or invalid credential → `pushEnabled: false`, one log line at first use, enqueue is a no-op,
  registrations still accepted, `POST /push/test` → `503 push_unavailable`. The app keeps working; Settings shows
  "Push is not set up on this server".
- **Android**: §4.1. The only secret is `GOOGLE_SERVICES_JSON_BASE64`, decoded in `release.yml` only.
- Workflow changes are human-reviewed: `release.yml` edits are human-only in this repo (AGENTS.md); the
  `deploy.yml` change ships in E8-S2's PR and needs the human's review like any production-deploy change.

### 6. Epic e2e (draft — written first by E8-S3, before its implementation)

`app/src/androidTest/.../e2e/PushJourneyTest.kt` + `docs/e2e/E8-push.md` (tag + fixture contract), every case
Ignored with reason `QMOB-E8` until its story removes that Ignore. Hermetic: signed-in `SessionSeedRule`,
`MockWebServer` fixtures, a fake `FcmTokenSource` (fixed token) bound with `@TestInstallIn`, `GrantPermissionRule`
for `POST_NOTIFICATIONS`. A "push" is injected by building
`RemoteMessage.Builder("quire@fcm.googleapis.com").setData(map).build()` and passing `.data` to the injected
`PushMessageHandler` — no Firebase initialisation, no `google-services.json`, no real FCM. Notifications are
asserted through `NotificationManager.activeNotifications` (channel, tag) and opened with
`notification.contentIntent.send()`.

| Case | Owner |
|---|---|
| `registration_afterPairing_putsTokenAndCategories` (MockWebServer sees `PUT /push/registration` with the fake token and all three categories) | E8-S3 |
| `filesAdded_single_postsNotificationOnFilesChannel` | E8-S3 |
| `unknownType_postsNothing` | E8-S3 |
| `signOut_deletesRegistrationThenRevokes` | E8-S3 |
| `filesAdded_single_tapOpensViewer_backReturnsToCourse` (`screen-pdf-viewer` → back → `screen-course`) | E8-S4 |
| `filesAdded_many_tapOpensCourse` | E8-S4 |
| `homeworkDue_tapOpensSheet` | E8-S4 |
| `noteSynthesized_ready_tapOpensNoteReader` | E8-S4 |
| `deletedFile_tapFallsBackToCourseWithMessage` | E8-S4 |
| `categoryOff_putsCategories_andSuppressesNotification` | E8-S5 |
| `sendTest_postsTestNotification` (`POST /push/test` → fixture 202, then injected `push.test`) | E8-S5 |

Server side: `apps/web/e2e/mobile-api.spec.ts` gains a push step run with `PUSH_TRANSPORT=fake` (§3.3), and every
other behaviour is covered by vitest against the fake transport and in-memory stores.

## Consequences

- Push adds two tables, an in-process loop and four call sites on the server; all are no-ops without the
  credential. The in-process loop assumes one app process (true on the Pi); a second replica would need a row lock
  (`FOR UPDATE SKIP LOCKED`) in `dispatchDue`, noted in code.
- Payloads stay content-free; notification text costs one small API call on receipt, with an offline fallback.
- Builds without `google-services.json` are first-class; only release builds require it.
- OEM battery management (OxygenOS on the OnePlus 15R) can delay or drop pushes for a restricted app; this is
  checked by hand in the epic's real-device acceptance, not in CI (CI-SPEC §6 already lists real FCM delivery as an
  accepted gap).
- The E7 "N new files downloaded" notification can follow a `files.added` push for a pinned course. Accepted for
  v1 (different channels, both user-controlled).
- New categories later are additive: a new `type` + category on the server, a new channel + toggle in the app;
  older apps drop unknown types and the server ignores unknown categories.

## Story split

### E8-S2 — Server: device registration + FCM sender (EzraGubbay/quire#41)

- `PUT`/`GET`/`DELETE /api/mobile/v1/push/registration` and `POST /push/test` exactly as §3.1 (auth, validation,
  idempotency, token move on re-pair, `pushEnabled`, `push_unavailable`, rate limit).
- Migration for `mobile_push_registrations` and `mobile_push_outbox` (§3.2); revoking a device (API or web) deletes
  its registration and pending outbox rows.
- `lib/mobile/push/` modules as §3.3; `fcmTransport` request shape and error mapping (§3.4) unit-tested with a
  fake `fetch`; everything else against `fakeTransport` and in-memory stores.
- Triggers: `importLemidaFile` (with coalescing), `runSynthesis` success and failure, `scanDeadlines` — each
  covered by a test that asserts the enqueued payload and dedupe (running the scan twice enqueues once; moving
  `due_at` re-arms; a duplicate Lemida file enqueues nothing; a burst of 5 imports dispatches one `count=5`).
- Degradation: no/invalid credential → disabled, logged once, no throws into callers.
- `deploy.yml` step, `infra/compose.yml` env, `infra/.env.example` (§5).
- OpenAPI, zod, `docs/mobile-api.md`, fixtures (incl. `push.*.json`) regenerated; fixture staleness and drift tests
  green; `e2e/mobile-api.spec.ts` push step (register → get → test push 202 → revoke → registration gone).

### E8-S3 — Android: FCM registration, channels, permission, receive (#63)

- First: `PushJourneyTest` (all §6 cases, Ignored with reason `QMOB-E8`) and `docs/e2e/E8-push.md`; vendored
  `push.*.json` and push-registration fixtures from quire.
- Gradle per §4.1 (`assembleDebug` and all tests pass with no `google-services.json`; `PUSH_CONFIGURED` false).
- `PushRegistrar` + worker + sign-out order (§4.2); `QuireMessagingService`, `PushPayloadParser`,
  `PushMessageHandler`, `PushNotificationFactory`, channels (§4.3–4.4); permission card + Settings fallback.
- Unit tests first: payload parsing (every fixture + unknown/missing fields), event → notification mapping
  (channel, tag, text, target extras), registrar fingerprint/skip logic, sign-out order.
- Un-ignores its four §6 cases.
- Human follow-up (not this story's PR): `release.yml` decode step (§4.1).

### E8-S4 — Deep links (#64)

- `PushTarget` parsing + back stack, `AppViewModel.openFromPush`, `AppShell` consumption, `PushTargetResolver`
  fallbacks (§4.5); cold and warm start.
- Unit tests first for `PushTarget.fromIntent`, `backStack()` and resolver outcomes (200 / 404 / timeout).
- Un-ignores its five §6 cases.

### E8-S5 — Preferences (#65)

- Settings → Notifications: three category switches (default on), per-channel system-settings link, "Blocked in
  system settings" state, "Send test notification" (`push_unavailable` and no-registration states shown).
- Toggle → DataStore → `PushRegistrar.sync()` → `PUT` with categories; handler re-checks locally.
- Unit tests first for the preference model and its mapping to `categories`.
- Un-ignores its two §6 cases.
