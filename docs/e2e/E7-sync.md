# E7 — Background sync / upload (periodic metadata sync · offline pinning · upload): e2e contract

Epic: [QMOB-E7 (#8)](https://github.com/EzraGubbay/quire-android/issues/8) ·
Journey defined first by [QMOB-E7-S0 (#50)](https://github.com/EzraGubbay/quire-android/issues/50).

This is the **contract** the sync/upload stories (#51–#55) build to. The instrumented journey
(`app/src/androidTest/java/com/ezragubbay/quire/e2e/SyncJourneyTest.kt`) finds UI **by test tag only**
(kebab-case, never by display text — so Hebrew/RTL course names are found the same way as Latin ones)
and runs against `MockWebServer` fixtures whose shapes mirror the real `mobile-v1` browse/content
contract. The session is seeded signed-in (`SessionSeedRule(signedIn = true)`) pointing at the
hermetic `MockWebServer`. Every sync case ships Ignored with reason `QMOB-E7`; a story removes that
Ignore from the cases it satisfies. The epic is done when no such Ignore remains in `SyncJourneyTest`
(a release PR into `main` fails while any remains).

> The literal `@Ignore` annotation text is written **only** in the real annotations in
> `SyncJourneyTest.kt`. Docs and KDoc describe the convention as "Ignored with reason QMOB-E7" so the
> release-gate grep (over `app/src/androidTest` for the `QMOB`-reason Ignore annotation) matches
> annotations only — the false-match lesson from E2's PR #78/#79, carried through E3/E4/E5.

> **Robust waits (the E4 journey-sync lesson).** Each case awaits a **content** node — a populated
> file row (`file-row-{fileId}`), a rendered page (`pdf-page-1`), a settings control (`action-sync-now`)
> — never a bare screen-root tag that is also present while the screen is Loading. A story that
> un-ignores a case must place the content tag only once the content is actually on screen, so later
> stories' cases do not flake at the CI gate.

## Background work is driven by the WorkManager test driver

Unlike the earlier journeys, E7 behaviour is background work, so `SyncJourneyTest` installs a **test
WorkManager** (`WorkManagerTestInitHelper.initializeTestWorkManager` with a `SynchronousExecutor`).
Scheduled work then runs only when the test releases it:

- **Periodic metadata sync.** The app schedules a `PeriodicWorkRequest` at launch under the unique
  name **`quire-sync`**. The test fetches it (`getWorkInfosForUniqueWork("quire-sync")`) and triggers
  it with `TestDriver.setPeriodDelayMet(id)` — no waiting on the real interval.
- **Pinned auto-download.** A pinned course enqueues a one-time download per new/changed file, keyed by
  the E5 `WorkManagerDownloadScheduler.uniqueName(fileId)` (`download-{fileId}`). The test meets its
  network constraint with `TestDriver.setAllConstraintsMet(id)`.

`SyncJourneyTest` wires workers through a `DelegatingWorkerFactory` that today delegates only to the E5
`DownloadWorkerFactory`. **QMOB-E7-S1 must chain its sync worker factory into that delegating factory
when it un-ignores the periodic-sync cases** (a factory returning `null` falls through to WorkManager's
default). Adding `androidx.work:work-testing` to the `androidTest` classpath (done by this story) is
what makes the test driver available in instrumented tests.

## The mobile API this talks to

Sync reads the same versioned mobile API (`/api/mobile/v1`) the browse/reader surfaces use; every call
carries `Authorization: Bearer <token>`. Metadata sync refetches the course list and per-course
sessions/files/notes and upserts them into Room (the offline-first cache from E4); pinned auto-download
streams `…/files/{fileId}/content?format=pdf` into app-private storage via the E5 download engine. The
error envelope is `{ "error": { "code", "message" } }`.

| Operation | Success | Notes |
|---|---|---|
| `GET /courses` | `200` (list) | Metadata sync refetch of the course list. |
| `GET /courses/{course}` + `.../sessions` + `.../files` + `.../notes` | `200` each | Per-course metadata; `…/files` is where a newly-added file first appears. |
| `GET /courses/{course}/files/{fileId}/content?format=pdf` | `200` / `206` (Range) | Pinned auto-download; `Accept-Ranges`, strong `ETag`, `Content-Disposition` (E5 contract). |

### Upload (pending — QMOB-E1-S8 lands first)

The mobile upload endpoints are **not yet exercised here**. Once the backend upload contract
(QMOB-E1-S8, async + idempotent) is fixed and mirrored into `androidTest/assets/fixtures/`, the upload
operations (initiate / status, resumable chunk `PUT`s) get their own fixtures and cases — see
[Pending: upload journeys](#pending-upload-journeys-qmob-e7-s3s5).

## The user journey

1. **Sync settings.** Settings (E2) grows a Sync section: `setting-sync-enabled` (on/off),
   `setting-sync-interval`, `setting-sync-wifi-only`, the `sync-last-success` stamp and `action-sync-now`
   (force a refetch now). A successful sync stamps `sync-last-success`.
2. **Periodic sync.** The app schedules a periodic metadata sync (`quire-sync`). When it runs, a file
   newly added on the server appears in the open course's Files tab (`file-row-{fileId}`) **without a
   manual pull-to-refresh** — the tab observes Room and the worker upserts into it.
3. **Pin a course.** `action-pin-course` marks a course 'Available offline'; `pinned-badge` marks it as
   pinned. Pinning (and each later sync) auto-downloads the course's files via the E5 download engine.
4. **Offline.** With a pinned course's file downloaded, the server can vanish and the file still opens
   (`pdf-page-1` renders from disk).
5. **Storage cap.** When a pin's offline footprint would exceed the configured storage cap,
   `storage-cap-warning` surfaces (the pin is blocked or needs confirmation — QMOB-E7-S2's call).
6. **Upload (pending).** Share-to-Quire → course picker → background upload queue → upload status. See
   below; specified once QMOB-E1-S8 is fixed.

## Test-tag contract

Kebab-case, stable tags the sync stories **must** honor. File ids and course ids are interpolated into
the `{…}` segments.

### Sync settings & periodic sync — placed by QMOB-E7-S1 (#51)

| Tag | Meaning |
|---|---|
| `setting-sync-enabled` | Background-sync on/off switch in Settings. |
| `setting-sync-interval` | Sync interval control (how often periodic sync runs). |
| `setting-sync-wifi-only` | 'Sync on Wi-Fi only' constraint toggle. |
| `sync-last-success` | Last-successful-sync timestamp shown in Settings. |
| `action-sync-now` | Force an immediate metadata sync. |

### Offline pinning — placed by QMOB-E7-S2 (#52)

| Tag | Meaning |
|---|---|
| `action-pin-course` | Pin / unpin the course 'Available offline'. |
| `pinned-badge` | Marks a course as pinned (available offline). |
| `storage-cap-warning` | Shown when a pin's offline footprint would exceed the storage cap. |

Inherited from earlier epics and reused to reach the surfaces above: `screen-courses`, `course-list`,
`course-card-{slug}`, `screen-course`, `course-tabs`, `tab-files`, `file-list`, `file-row-{fileId}`
(E4); `screen-pdf-viewer`, `pdf-page-{n}`, `download-row-{fileId}` (E5); `action-settings`,
`screen-settings` (E2).

## Cases → owning story

Each `SyncJourneyTest` case is Ignored with reason `QMOB-E7` and carries a `//` comment naming the story
that un-ignores it.

| `SyncJourneyTest` case | Un-ignored by |
|---|---|
| `syncSettings_exposesSyncControls` | QMOB-E7-S1 (#51) |
| `syncNow_fromSettings_stampsLastSuccess` | QMOB-E7-S1 (#51) |
| `periodicSync_surfacesNewServerFile_withoutManualRefresh` | QMOB-E7-S1 (#51) |
| `pinCourse_marksAvailableOffline_showsPinnedBadge` | QMOB-E7-S2 (#52) |
| `pinnedCourse_autoDownloadsSyncedFile_opensOffline` | QMOB-E7-S2 (#52) |
| `pinningOverStorageCap_showsWarning` | QMOB-E7-S2 (#52) |

## Pending: upload journeys (QMOB-E7-S3..S5)

These are **not** in `SyncJourneyTest` yet. The backend mobile upload contract (QMOB-E1-S8 — async +
idempotent) is fixed first; its request/response shapes are then mirrored into
`androidTest/assets/fixtures/` and these cases are added, each Ignored with reason **`QMOB-E7-upload`**
(a distinct reason from `QMOB-E7` so the sync epic can close before upload ships, and so the human can
defer upload per the epic's definition of done). Planned journey and tags (draft — settled when the
contract lands):

- **Share to Quire (QMOB-E7-S4, #54).** An Android `ACTION_SEND` of a PDF opens a course picker and
  enqueues an upload. Draft tags: `screen-share-target`, `share-course-picker`, `share-course-{slug}`,
  `action-enqueue-upload`.
- **Upload queue engine (QMOB-E7-S3, #53).** Background, resumable, create-only. Verified mostly by its
  own unit tests (resume, idempotency key); surfaces state to the status screen below.
- **Upload status (QMOB-E7-S5, #55).** A queue screen with per-item progress, a progress notification,
  and failure recovery. Draft tags: `screen-uploads`, `upload-row-{id}`, `upload-state-queued`,
  `upload-state-running`, `upload-state-done`, `upload-state-failed`, `action-retry-upload`,
  `action-cancel-upload`.

## Fixtures

`app/src/androidTest/assets/fixtures/` (served by `MockWebServerRule`). The browse fixtures
(`courses-list.json`, `course-detail.json`, `course-sessions.json`, `course-files.json`,
`course-notes.json`) and the PDF deck (`lecture-1.pdf`) are reused from E4/E5. E7 adds:

| Fixture | Mirrors | Served as | Used by |
|---|---|---|---|
| `course-files-synced.json` | `listFiles.200.json` with one extra file ('Lecture 2 - Sampling', `hasPdf`) | `enqueueFixture` (JSON) | periodic-sync (new row appears), pinned auto-download |

`course-files-synced.json` is `course-files.json` plus the new `Lecture 2 - Sampling` file
(`fileId = b2c3d4e5-6f70-4a81-9b2c-3d4e5f607182`); it is served on the post-sync refetch so the new
`file-row-{fileId}` appears without a manual refresh, and its PDF (`lecture-1.pdf`, via
`enqueuePdfFixture`) is the body the pinned auto-download streams. Upload fixtures are added with the
pending upload cases once QMOB-E1-S8 is fixed.
