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

### Upload (shipped — QMOB-E1-S8 + API 1.2.0)

| Operation | Status | Role |
|---|---|---|
| `POST /courses/{course}/uploads?kind=document\|note&name=…` (raw body, `Idempotency-Key`) | `202` | Queue one file: PDF (`application/pdf`) or Markdown (`text/markdown`). PDF and Markdown-on-Files are documents, Markdown-on-Notes is a note. |
| `GET /uploads/{uploadId}` | `200` | Poll the job: `queued` / `processing` / `done` (`fileId` or `noteId`) / `failed` (`error`). |

Limits: PDF 100 MB, Markdown 2 MB. A server-`failed` job is final for its key, so Retry after it sends a new key.

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
6. **Upload.** The Files and Notes tabs each have an Upload button (`action-upload-file`, `action-upload-note`).
   The system picker (multi-select) offers PDF and Markdown; the chosen file is copied into app-private storage and
   queued. A pending or failed upload shows as a row above the list (`upload-row-{id}`) with its state; on `done`
   the course is refreshed and the new file or note appears. A PDF picked on the Notes tab is saved to Documents
   (the app says so). Share-to-Quire (#54) and a dedicated queue screen with notifications (#55) are still to come.

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

## Upload journeys (QMOB-E7-S3, #53) — `UploadJourneyTest`

Live, not ignored. The system picker is stubbed with Espresso-Intents; background work is driven by the WorkManager
test driver (the upload worker runs once its network constraint is met).

| Tag | Meaning |
|---|---|
| `action-upload-file` | Upload button on the Files tab (PDF or Markdown becomes a document). |
| `action-upload-note` | Upload button on the Notes tab (Markdown becomes a note; a PDF becomes a document). |
| `upload-section` | The "Uploads" block above a tab's list (only while something is pending or failed). |
| `upload-row-{id}` | One queued, running, processing or failed upload (`{id}` is the queue id, also the idempotency key). |
| `upload-state-queued` / `upload-state-running` / `upload-state-processing` / `upload-state-failed` | The state label inside a row. |
| `action-retry-upload` | Retry a failed upload (a server-failed one is re-sent under a new key). |
| `action-cancel-upload` | Cancel a queued, running or processing upload. |
| `action-dismiss-upload` | Remove a failed upload. |

| Case | Covers |
|---|---|
| `pdfFromFilesTab_uploads_andTheNewFileAppears` | Files tab: POST (key, type, kind, name), poll to done, row disappears, new `file-row-{id}`. |
| `markdownFromNotesTab_uploads_andTheNewNoteAppears` | Notes tab: `kind=note`, new `note-row-{id}`. |
| `pdfFromNotesTab_isUploadedAsADocument` | A PDF on Notes is sent as `kind=document`, never refused. |
| `serverFailure_showsTheFailedState_withRetry` | `upload-state-failed` with `action-retry-upload` and `action-dismiss-upload`. |

Still to come: Share-to-Quire (#54: share target, course picker) and the queue screen with progress and result
notifications (#55).

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
`enqueuePdfFixture`) is the body the pinned auto-download streams. 

The upload journey adds `upload-accepted.json`, `upload-done-document.json`, `upload-done-note.json`,
`upload-failed.json` (the job as the server reports it), `course-files-uploaded.json` and
`course-notes-uploaded.json` (the refetched lists with the new file / note) and `week1.md` (the Markdown the picker
returns). The picked `file:` URI points at a copy of a fixture in the app cache.
