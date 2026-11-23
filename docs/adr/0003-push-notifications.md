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
| `homework.due` | `deadlines` → `push_deadlines` (high) | New scheduler tick (`lib/mobile/push/scheduler.ts` `scanDeadlines()`, every 10 min): assignments with `due_at` in `(now, now + 24 h]` and status `not_started` or `in_progress`, in projects with `kind = 'course'` and `status = 'active'` | `course`, `courseId`, `assignmentId`, `number`, `dueAt` (ISO-8601), `fileId` (the sheet document, omitted when none) | Title: "Homework {number} due {today/tomorrow HH:mm}" (device time zone). Body: course name, plus the sheet title when fetched | `fileId` present: viewer on the sheet; else the course | `HIGH` / until `dueAt` (max 24 h) | `homework.due:{assignmentId}:{dueAt epoch s}` (a moved deadline re-arms) |
| `note.synthesized` | `notes` → `push_notes` (default) | `lib/ai/synthesis.ts` `runSynthesis()`: `outcome=ready` is enqueued **after** `syncLinks()` (so the note is complete when the phone fetches it); `outcome=failed` inside `fail()`. Notes that `failStaleGenerating()` marks failed at boot send nothing. | `course`, `courseId`, `noteId`, `outcome` (`ready`\|`failed`) | ready: "Study note ready" / note title. failed: "Note synthesis failed" / "Open Quire on the web to retry." | ready: note reader; failed: the course | `NORMAL` / 24 h | `note.synthesized:{noteId}:{outcome}:{note updatedAt ms}` (each run is distinct) |
| `push.test` | none (always on) → `push_system` (low) | `POST /api/mobile/v1/push/test` from the app's Settings (§3); calling device only; sent synchronously | — | "Quire notifications work" / "Test sent from your server." | Courses (home) | `HIGH` / 5 min | `push.test:{eventId}` |

`fileId` is always a `documents.id`, the same id the mobile API calls `fileId`. For `files.added` it is the import's
`placement.documentId`. For `homework.due` it is the assignment's `sheet_document_id`.

Only active course projects produce events: `audienceForCourse()` (§3.3) returns nobody for archived or research
projects.

"Tap opens" always builds a real back stack: Courses → course → target (§4.5).

**Coalescing `files.added`.** The Lemida agent imports a course's new files one request at a time, and a newly
mapped course can bring dozens.

- **Enqueue.** Each import enqueues one outbox row with `group_key = files:{courseId}`, no `batch_id`, and
  `not_before = now + 90 s`. Enqueueing slides the group's *unbatched* pending rows to
  `not_before = greatest(not_before, least(now + 90 s, oldest created_at + 10 min))`. It only ever moves `not_before`
  later, so it never shortens a retry backoff.
- **Freeze on first send.** When the rows of a (registration, group) are due, the dispatcher stamps a fresh
  `batch_id` on exactly the selected row ids (`… WHERE id IN (…) AND batch_id IS NULL`) and sends **one**
  `files.added` with:
  - `eventId = batch_id`;
  - `count` = the rows in the batch;
  - `fileId` = the newest row's document.
- **Retries** resend exactly that batch, with the same `eventId`, `count` and `fileId`.
- **Completion.** Rows are marked sent or dead by their batch's row ids (`WHERE batch_id = $1`), never by
  `group_key`.
- **Late rows.** Rows that arrive after a batch was frozen get no `batch_id` and form the next batch. While a
  (registration, group) still has a batch awaiting retry, its newer rows wait, so an older batch can never arrive
  after a newer one.
- **On the phone** the notification tag is `files:{courseId}`, so a newer batch replaces the older notification for
  that course.

The result: a single file notifies about 90 s after import, and a burst becomes one "N new files" notification.
Every message, not only `files.added`, gets a `batch_id`: for ungrouped events the batch is the single row, and
`eventId = batch_id` everywhere.

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
- **TTL** per the table. `android.ttl` is always `expires_at − now`, computed at each attempt, so a retried message
  never outlives the event's original TTL. An outbox row whose TTL has lapsed is dropped.
- **Collapse keys: not used**, except `push.test` (`collapse_key: "push.test"`). Collapsing data messages would
  drop distinct events stored for an offline phone, and coalescing already happens server-side.
- **Tags.** On the phone the notification **tag** replaces duplicates and older versions:

  | Event | Tag |
  |---|---|
  | `files.added` | `files:{courseId}` |
  | `homework.due` | `homework:{assignmentId}` |
  | `note.synthesized` | `note:{noteId}` |
  | `push.test` | `test` |

  A redelivered or retried message (same `eventId`) therefore replaces the existing notification instead of
  duplicating it.
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

**`POST /push/test`** (no body) sends `push.test` to the calling device **synchronously**, within the request, and
bypasses categories and coalescing. The FCM result is reported back:

| Result | Response |
|---|---|
| FCM accepted the message | `200 { "eventId": "<uuid>", "outcome": "sent" }` |
| FCM answered `UNREGISTERED` | `200 { "eventId": "<uuid>", "outcome": "unregistered" }`. The registration has already been deleted, and the app should run its registration again. |
| Retryable FCM failure (§3.4) | `200 { "eventId": "<uuid>", "outcome": "retryable" }`. Nothing is queued. |
| Any other FCM failure (§3.4) | `200 { "eventId": "<uuid>", "outcome": "failed" }`. Nothing is queued. |
| The device has no registration | `400 invalid_request` |
| More than 1 request per 30 s per device | `429 rate_limited` (existing `lib/mobile/rate-limit.ts`) |
| Push is disabled on the server | **`503 push_unavailable`**, a new error code that only this endpoint returns |

The send is still recorded in the outbox as a single-row batch, with dedupe key `push.test:{eventId}`.

OpenAPI (`docs/api/mobile-v1.openapi.yaml`), zod schemas (`lib/mobile/schema.ts`), `docs/mobile-api.md` and the
generated fixtures gain these operations (`putPushRegistration.request.json`, `putPushRegistration.200.json`,
`getPushRegistration.200.json`, `sendTestPush.200.json`, `error.push_unavailable.json`) **plus one fixture per push
payload** (`push.files.added.json`, `push.homework.due.json`, `push.note.synthesized.json`, `push.test.json`, the
`data` map exactly as sent, generated from the pure `events.ts` builders) so the app's contract tests decode what the
server emits. `GET /me` `apiVersion` bumps minor.

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
  last_error     text                     -- last non-retryable error code (enum, never a raw message)

mobile_push_outbox                        -- one row per (event, registration)
  id             uuid pk default random
  registration_id uuid not null → mobile_push_registrations.id on delete cascade
  type           text not null            -- 'files.added' | 'homework.due' | 'note.synthesized' | 'push.test'
  dedupe_key     text not null
  group_key      text                     -- 'files:{courseId}' for coalescing; null otherwise
  batch_id       uuid                     -- stamped on first send attempt; = the message's eventId (§1)
  data           jsonb not null           -- the FCM data map (string values; eventId/count/fileId set per batch)
  priority       text not null            -- 'HIGH' | 'NORMAL'
  expires_at     timestamptz not null     -- created_at + TTL; past it the row is dropped, not sent
  not_before     timestamptz not null default now()
  attempts       int not null default 0
  created_at     timestamptz not null default now()
  sent_at        timestamptz
  dead_at        timestamptz              -- gave up (expired, unregistered, config-fatal, attempts exhausted)
  last_error     text                     -- enum code (e.g. 'expired', 'UNAVAILABLE'), never a raw message
  unique (registration_id, dedupe_key)
  index (not_before) where sent_at is null and dead_at is null     -- partial: the dispatcher's due scan
  index (batch_id)
```

Both tables, including `group_key`, `not_before` and `batch_id`, ship in **one** migration, in E8-S2a.

**Revocation deletes push state.** `mobile_tokens` revocation is a soft `revoked_at` update, so the FK cascade does
not fire on revoke. `dbStore.revoke()` in `lib/mobile/auth.ts` therefore also deletes the device's registration
(same transaction; outbox rows cascade). This covers `DELETE /auth/token` and web Settings → Mobile devices →
Revoke. The dispatcher additionally only sends to registrations whose token has `revoked_at IS NULL`.

#### 3.3 Sender module — `apps/web/src/lib/mobile/push/`

| File | Responsibility |
|---|---|
| `config.ts` | `pushConfig()`: memoised. Reads `FCM_SERVICE_ACCOUNT_JSON_BASE64`, decodes, validates `type = service_account`, `project_id`, `client_email`, `private_key`. Returns `{ enabled: true, projectId, clientEmail, privateKey }` or `{ enabled: false, reason }`, where `reason` is a **fixed enum**: `missing` \| `bad_base64` \| `bad_json` \| `wrong_type` \| `missing_field:<name>`. Logs **once** `[push] disabled: <reason>`. It never logs `err.message`, because `JSON.parse` errors quote the input, which is key material, and `deploy.yml`'s rollback dumps app logs into the Actions log. `PUSH_TRANSPORT=fake` → enabled with the fake transport (dev, CI e2e). |
| `transport.ts` | `interface PushTransport { send(m: { token, data, priority, ttlSeconds, collapseKey? }): Promise<SendResult> }`, `SendResult = { ok: true } \| { ok: false, kind: 'unregistered' \| 'retryable' \| 'config' \| 'fatal', errorCode, retryAfterMs? }`. `errorCode` is the FCM error code or an HTTP status, never a raw body. `fcmTransport(config, fetch = globalThis.fetch)`: OAuth2 access token from a self-signed RS256 JWT (`node:crypto`, scope `https://www.googleapis.com/auth/firebase.messaging`, exchanged at `https://oauth2.googleapis.com/token`, cached until 5 min before expiry), then `POST https://fcm.googleapis.com/v1/projects/{projectId}/messages:send`. No new dependency. It **never logs** the JWT, the access token, OAuth response bodies or FCM tokens. `fakeTransport()` records messages and can be scripted per token (tests); under `PUSH_TRANSPORT=fake` it logs `[push:fake] sent type=<type> registration=<id>`. |
| `events.ts` | Pure builders, one per type: `filesAdded()`, `homeworkDue()`, `noteSynthesized()`, `pushTest()` → `{ type, category, dedupeKey, groupKey?, data, priority, ttlSeconds }`. zod schema per payload; the `push.*.json` fixtures are generated from these builders. |
| `outbox.ts` | `enqueuePush(audience, event)`: no-op when push is disabled; inserts one row per matching registration (category enabled, token active) with `ON CONFLICT (registration_id, dedupe_key) DO NOTHING`; for `group_key` rows, slides the group's unbatched `not_before`, never earlier (§1). Never throws into the caller: trigger sites wrap it so an import or synthesis can never fail because of push. |
| `dispatcher.ts` | `dispatchDue(now, deps)`: due rows via the partial index (`sent_at IS NULL AND dead_at IS NULL AND not_before <= now`, limit 100). Expired rows → `dead_at`, `last_error = 'expired'`. Batches are frozen per §1: `batch_id` is stamped on the selected row ids, and a (registration, group) with a batch awaiting retry holds back newer rows. Each batch is sent with `eventId = batch_id` and `ttl = expires_at − now`; outcomes per §3.4. Re-entrancy guard (one run at a time). Also exports `sendNow(row)`, which `POST /push/test` uses. |
| `scheduler.ts` | `startPushLoop()`, nodejs runtime only, idempotent across dev hot-reloads via a `globalThis` flag. Every 30 s `dispatchDue`; every 10 min `scanDeadlines`; daily, delete outbox rows sent or dead more than 30 days ago. Timers are `unref()`'d so they never keep a process alive (tests, scripts). `instrumentation.ts` calls it on **every** boot, after migrations when they run. It must not sit inside the code path that `SKIP_MIGRATIONS=1` returns early from: today that is an early `return`, so `register()` is restructured to run the migration block conditionally and then start the loop. |
| `audience.ts` | `audienceForCourse(projectId)`: the owner logins whose devices may hear about a course. It returns **nobody** for projects that are archived (`status = 'archived'`) or not courses (`kind <> 'course'`). Projects have no owner column today, so for an active course it returns every owner with an active registration; this is the single seam to change when courses gain owners. `push.test` targets the calling device directly. |
| `registrations.ts` | `RegistrationStore` seam (SQL `dbRegistrations` + in-memory for tests, like `AuthStore`): upsert, get, delete, delete-by-token-id. |

#### 3.4 Delivery outcomes and dedupe

- **Dedupe**: at enqueue via `unique (registration_id, dedupe_key)` (the 10-minute deadline scan and a re-run
  import cannot double-notify); on the phone via the notification tag (§2).
- **Server-side category filter**: rows are only created for registrations whose `categories` contain the event's
  category, so a disabled category never wakes the phone.
- Outcomes are classified by the **FCM error code**, which is the `errorCode` of the
  `type.googleapis.com/google.firebase.fcm.v1.FcmError` entry in `error.details[]`, plus any
  `google.rpc.BadRequest` field violations. The HTTP status alone never decides.
  - `200` → the batch's rows get `sent_at = now`, and the registration gets `last_sent_at`.
  - **Prune**, only for `UNREGISTERED`, or `INVALID_ARGUMENT` with a `BadRequest` field violation on `message.token`.
    The registration is deleted (its pending rows cascade) and `[push] pruned registration <id>` is logged.
  - **Config-fatal**: `SENDER_ID_MISMATCH` and `PERMISSION_DENIED`. These mean the server's credential or project
    does not match the app, not that the device is gone. The batch is marked `dead_at` with
    `last_error = <errorCode>`, `[push] FCM config error: <errorCode>` is logged once per process, and the
    **registration is kept**.
  - **Retryable**: `QUOTA_EXCEEDED`, `UNAVAILABLE`, `INTERNAL`, and network errors. The batch gets `attempts + 1` and
    `not_before = now + max(Retry-After, min(30 s · 2^attempts, 30 min))`; after 6 attempts it is marked `dead_at`.
    The retry resends the same frozen batch (§1).
  - `401` / `UNAUTHENTICATED` (access token rejected) → drop the cached access token and retry once in the same run.
    If it fails again, treat it as retryable and log `[push] FCM auth failed` at most once an hour.
  - Anything else (for example `INVALID_ARGUMENT` without a token violation, which is a payload bug) → `dead_at`
    with `last_error = <errorCode>`, because retrying cannot help.
- Logs and `last_error` hold only error codes and ids, never response bodies.
- A long FCM outage therefore costs nothing but bounded retries, and events older than their TTL are dropped rather
  than delivered late.

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

- `sync()`: if not signed in, or `!PUSH_CONFIGURED`, it does nothing. Otherwise it enables FCM auto-init, gets the
  token, and always sends `PUT /push/registration` with the current categories. There is no fingerprint skip: the PUT
  is idempotent and cheap. Sending it on every app start means that a registration the server pruned or dropped
  (for example after a config-fatal error was fixed, or after a database restore) comes back without any client
  bookkeeping. Failures are retried by `PushRegistrationWorker` (one-time unique work `push-registration`, network
  constraint, exponential backoff).
- `sync()` is called on:
  - pairing success (`AppViewModel.onSignedIn`);
  - **every app start** (alongside `SyncCoordinator.start()`);
  - `FirebaseMessagingService.onNewToken`, which enqueues the worker because the service has no UI scope;
  - category changes;
  - a `POST /push/test` result of `outcome: "unregistered"`.
- Sign-out (`AppViewModel.signOut`): best-effort `DELETE /push/registration` (inside the existing revoke timeout)
  → `DELETE /auth/token` (server deletes the registration anyway) → `FcmTokenSource.delete()` (invalidates the
  token at FCM, so a server that missed both deletes gets `UNREGISTERED` and prunes it) → disable auto-init.
  Session expiry (401, e.g. revoked from the web): the server already deleted the registration; the app runs the
  local half (delete the token, disable auto-init).
- Re-pairing (same or another server) runs `sync()` with the new device token; the server moves an existing FCM
  token to the new device row (§3.1).

#### 4.3 Receiving — `push/QuireMessagingService` + `push/PushMessageHandler`

- `QuireMessagingService : FirebaseMessagingService` (manifest: `exported="false"`,
  `com.google.firebase.MESSAGING_EVENT`) is a thin adapter: `onMessageReceived(m)` →
  `handler.handle(m.data)`; `onNewToken` → enqueue registration; `onDeletedMessages` → one-time metadata sync.
- `onMessageReceived` runs on FCM's background thread and has a short execution budget, so the handler does its
  work **synchronously** there, under **one overall deadline of about 8 s**
  (`runBlocking { withTimeoutOrNull(8.s) { … } }`). The name lookups run **in parallel** (`async`):
  - the course name;
  - the file or note title.

  Each lookup tries Room first, then the API. Whatever has not resolved by the deadline falls back to generic text,
  and the notification is posted in every case. No work is handed to a coroutine scope that outlives the call.
- `PushMessageHandler` (Hilt singleton, the unit under test):
  - parses the map into a sealed `PushEvent` with `PushPayloadParser` (pure; unknown `v`/`type` or missing ids are
    dropped);
  - checks the local category toggle (defence in depth for a server that is behind);
  - resolves display names as above;
  - builds the notification with `PushNotificationFactory` (a pure mapping from event to channel, tag, title/body
    and target);
  - posts it if notifications are enabled and permitted.
- `files.added` also enqueues a one-time **expedited** metadata sync (`setExpedited(OutOfQuotaPolicy
  .RUN_AS_NON_EXPEDITED_WORK_REQUEST)`), so pinned courses auto-download the new file (E7's "push-triggered sync").
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
  `MainActivity` hands it to `AppViewModel.openFromPush()`:
  - in `onCreate`, **only when `savedInstanceState == null`** and the intent does **not** carry
    `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`. This way a configuration change, process-death restore, or a relaunch
    from Recents does not replay an old notification;
  - in `onNewIntent` (warm start).
- The pending target is a `StateFlow` that `QuireApp` passes into `AppShell`, which consumes it once composed. It
  pops to `CoursesRoute`, then navigates to `CourseRoute(slug)` and, for files and notes, to
  `ViewerRoute(slug, fileId)` / `NoteReaderRoute(slug, noteId)`. Back therefore walks target → course → Courses.
  `PushTarget.backStack()` is pure and unit-tested.
- While `signedIn == null` (the session is still being restored from disk), the target is **held**. It is consumed
  once the session is signed in, and dropped only when the session is signed out (`false`), in which case the Pair
  screen shows.
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
  `/srv/quire/.env`:
  - The value is first stripped of whitespace by piping it through stdin (`printf '%s' "$FCM_B64" | tr -d '\r\n '`),
    because a pasted secret can carry line breaks.
  - The file is rewritten with `grep -v` plus the bash builtin `printf` into a temp file created under `umask 077`,
    then `cat` over `.env`, so its mode and owner stay.
  - No `set -x`, and the secret never appears on a command line.
  - An empty or missing secret removes the line, and the step logs "push disabled: no FCM credential".

  `infra/compose.yml` passes `FCM_SERVICE_ACCOUNT_JSON_BASE64: ${FCM_SERVICE_ACCOUNT_JSON_BASE64:-}` to `app`, and
  `infra/.env.example` documents the key (empty). Nobody edits the Pi by hand.
- **Rotating the key** means updating the Actions secret and then running the Deploy workflow (`workflow_dispatch`).
  The step only runs on deploy, and Compose recreates `app` because its environment changed. `docs/mobile-api.md`
  (or `infra/README.md`) states this.
- Why `.env` and not a mounted file: every other server secret already lives in `/srv/quire/.env`, and a bind
  mounted file would need ownership changes for the container's uid 1001 (the deploy user cannot `sudo` on
  researchpi). The base64 form keeps it one line. It is not in backups (the backup container only mounts the files
  volume and Postgres).
- **Degradation**: no or invalid credential → `pushEnabled: false`, one log line at first use, enqueue is a no-op,
  registrations still accepted, `POST /push/test` → `503 push_unavailable`. The app keeps working; Settings shows
  "Push is not set up on this server".
- **Android**: §4.1. The only secret is `GOOGLE_SERVICES_JSON_BASE64`, decoded in `release.yml` only.
- **Workflow changes:**
  - `release.yml` edits are human-only in this repo (AGENTS.md), so they are their own story: QMOB-E8-S6 (#155).
  - The `deploy.yml` and `infra/compose.yml` changes ship in E8-S2a's PR. The quire#41 agent may make them, and the
    human reviews them like any production-deploy change.
  - The quire `ci.yml` `e2e` job sets `PUSH_TRANSPORT=fake`, which is also part of E8-S2a.

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
| `sendTest_postsTestNotification` (`POST /push/test` → fixture 200 `outcome: sent`, then injected `push.test`) | E8-S5 |

Server side: `apps/web/e2e/mobile-api.spec.ts` gains a push step, and the quire `ci.yml` `e2e` job sets
`PUSH_TRANSPORT=fake` (§3.3). Every other behaviour is covered by vitest against the fake transport and in-memory
stores.

## Consequences

- Push adds two tables, an in-process loop and three trigger call sites on the server; all are no-ops without the
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

### E8-S2a — Server: registration API + FCM sender (EzraGubbay/quire#41)

- `PUT`/`GET`/`DELETE /api/mobile/v1/push/registration` and the synchronous `POST /push/test`, exactly as §3.1:
  auth, validation, idempotency, token move on re-pair, `pushEnabled`, `outcome`, `push_unavailable`, rate limit.
- **One** migration for both tables, including `group_key`, `not_before`, `batch_id` and the partial index (§3.2).
  Revoking a device (API or web) deletes its registration and pending outbox rows.
- `config`, `transport` (OAuth plus FCM error-detail classification, §3.4), `events` (pure builders for all four
  types), `outbox` (including the never-earlier group slide), `dispatcher` (batch freeze, retries, TTL recomputed
  per attempt), `scheduler` (unref'd timers, started on every boot regardless of `SKIP_MIGRATIONS`), `audience`
  and `registrations`. `fcmTransport` is unit-tested with a fake `fetch`; everything else runs against
  `fakeTransport` and in-memory stores.
- Degradation: no or invalid credential → disabled, with the enum reason logged once; nothing throws into callers.
- `deploy.yml` step (whitespace strip, rotation note), `infra/compose.yml` env, `infra/.env.example` (§5); quire
  `ci.yml` `e2e` sets `PUSH_TRANSPORT=fake`.
- OpenAPI, zod and `docs/mobile-api.md` updated, and fixtures regenerated, including all `push.*.json` from the
  `events.ts` builders. Fixture staleness and drift tests are green.
- `e2e/mobile-api.spec.ts` push step: register → get → test push `200 sent` → revoke → registration gone.

### E8-S2b — Server: push triggers (EzraGubbay/quire#53)

- The three triggers from §1:
  - `importLemidaFile` (`fileId = placement.documentId`);
  - `runSynthesis` (ready after `syncLinks`, failed in `fail()`, nothing on stale boot);
  - `scanDeadlines` (active course projects only).
- Coalescing behaviour end to end: a burst of 5 gives one `count=5`; a late row forms the next batch.
- The audience excludes archived and non-course projects.
- No migration.

### E8-S3 — Android: FCM registration, channels, permission, receive (#63)

- First: `PushJourneyTest` (all §6 cases, Ignored with reason `QMOB-E8`) and `docs/e2e/E8-push.md`; vendored
  `push.*.json` and push-registration fixtures from quire.
- Gradle per §4.1 (`assembleDebug` and all tests pass with no `google-services.json`; `PUSH_CONFIGURED` false).
- `PushRegistrar` + worker + sign-out order (§4.2); `QuireMessagingService`, `PushPayloadParser`,
  `PushMessageHandler`, `PushNotificationFactory`, channels (§4.3–4.4); permission card + Settings fallback.
- Unit tests first:
  - payload parsing (every fixture plus unknown/missing fields);
  - event → notification mapping (channel, tag `files:{courseId}` etc., text, target extras);
  - handler deadline behaviour (a slow lookup still posts the fallback text within the deadline);
  - registrar (PUT on every start, not-configured no-op, sign-out order).
- Un-ignores its four §6 cases.

### E8-S4 — Deep links (#64)

- `PushTarget` parsing and back stack, `AppViewModel.openFromPush`, `AppShell` consumption, `PushTargetResolver`
  fallbacks (§4.5); cold and warm start.
- The intent is handled only when `savedInstanceState == null` and without `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`.
  The target is held while `signedIn == null`.
- Unit tests first for `PushTarget.fromIntent`, `backStack()`, the replay guards and the held/dropped target, and
  resolver outcomes (200 / 404 / timeout).
- Un-ignores its five §6 cases.

### E8-S5 — Preferences (#65)

- Settings → Notifications: three category switches (default on), per-channel system-settings link, "Blocked in
  system settings" state.
- "Send test notification" shows each `outcome` (`unregistered` → re-register) and the `push_unavailable`,
  no-registration and rate-limited states.
- Toggle → DataStore → `PushRegistrar.sync()` → `PUT` with categories; the handler re-checks locally.
- Unit tests first for the preference model and its mapping to `categories`.
- Un-ignores its two §6 cases.

### E8-S6 — `release.yml`: decode `GOOGLE_SERVICES_JSON_BASE64` (#155, human-only)

- Decode the secret to `app/google-services.json` before `assembleRelease` (umask 077). Fail the release if the
  secret is missing, and delete the file afterwards (§4.1).
