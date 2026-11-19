# E5 — Reader (downloads / PDF viewer / open-file / downloads library / share / notes): e2e contract

Epic: [QMOB-E5 (#6)](https://github.com/EzraGubbay/quire-android/issues/6) ·
Journey defined first by [QMOB-E5-S0 (#35)](https://github.com/EzraGubbay/quire-android/issues/35).

This is the **contract** the reader stories (#36–#42) build to. The instrumented journey
(`app/src/androidTest/java/com/ezragubbay/quire/e2e/ReaderJourneyTest.kt`) finds UI **by test tag
only** (kebab-case, never by display text — so Hebrew/RTL titles and formulae are found the same way
as Latin ones) and runs against `MockWebServer` fixtures whose shapes mirror the real `mobile-v1`
file-content and note contracts. The session is seeded signed-in (`SessionSeedRule(signedIn = true)`)
pointing at the hermetic `MockWebServer`. Every case ships Ignored with reason `QMOB-E5`; a story
removes that Ignore from the cases it satisfies. The epic is done when no such Ignore remains in
`ReaderJourneyTest` (a release PR into `main` fails while any remains).

> The literal `@Ignore` annotation text is written **only** in the real annotations in
> `ReaderJourneyTest.kt`. Docs and KDoc describe the convention as "Ignored with reason QMOB-E5" so
> the release-gate grep (over `app/src/androidTest` for the `QMOB`-reason Ignore annotation) matches
> annotations only — the false-match lesson from E2's PR #78/#79, carried through E3/E4.

> **Robust waits (the E4 journey-sync lesson).** Each case awaits a **content** node — a rendered
> page (`pdf-page-1`), a populated row (`download-row-{fileId}`), the reader body (`note-markdown`) —
> never a bare screen-root tag that is also present while the screen is Loading. A story that
> un-ignores a case must therefore place the content tag only once the content is actually on screen,
> so later stories' cases do not flake at the CI gate.

## The mobile API this talks to

The reader streams bytes from the versioned mobile API (`/api/mobile/v1`); every call carries
`Authorization: Bearer <token>`. Shapes are copied from `EzraGubbay/quire`
`docs/api/mobile-v1.openapi.yaml` + `docs/api/fixtures/mobile-v1/`:

| Operation | Success | Notes |
|---|---|---|
| `GET /courses/{course}/files/{fileId}/content?format=pdf\|original\|markdown` | `200` (full body) / `206` (Range) | `Accept-Ranges: bytes`, strong `ETag`, `Content-Disposition` filename. `304` on `If-None-Match`, `416` on an unsatisfiable `Range`, `404` not found. `format` defaults to `pdf`. |
| `GET /courses/{course}/notes/{noteId}` | `200 { id, slug, title, updatedAt, markdown }` | Raw Markdown body (headings, lists, tables, inline/block LaTeX); read-only. |

The download engine (#36) streams `…/content?format=pdf` into app-private storage with `Range` +
`If-Range` (ETag) resume; the viewer (#37) renders the cached PDF with the platform `PdfRenderer`;
open-file (#38) decides disk-vs-stream and routes Markdown to the notes reader and originals to
`Open with…`. The error envelope is `{ "error": { "code", "message" } }`.

## The user journey

1. **Open a file.** From the Files tab (or a session deck / a search result) a tap on
   `file-row-{fileId}` runs the open-file flow: a downloaded file opens from disk, otherwise its PDF
   format is streamed into a size-capped cache with determinate `viewer-progress`, then shown.
2. **PDF viewer.** `screen-pdf-viewer` lazily renders `pdf-page-{n}` off the main thread at viewport
   resolution; `page-indicator` reads 'n / N' with `action-jump-to-page`. Scrolling renders further
   pages; double-tap / pinch zooms (1×–5×) and re-renders crisply; a tap toggles immersive mode.
3. **Resume.** The last-read page per file persists (Room) and is restored on reopen; rotation keeps
   page and zoom.
4. **Download toggle.** The `viewer-top-bar` (and the `FileRow` trailing slot) show the download state
   and action: `download-state-idle` → `viewer-download-toggle` enqueues → `download-state-running`
   (with progress) → `download-state-done`, or `download-state-failed` with retry. A done download is
   persisted offline.
5. **Offline.** With the file downloaded, the server can vanish and reopening still renders from disk.
6. **Downloads tab.** `screen-downloads` lists downloaded and in-progress files
   (`downloads-list` → `download-group-{courseId}` → `download-row-{fileId}`) with size and state;
   `storage-usage` in the header shows total space used; `download-remove` deletes one (file + row),
   `download-retry` restarts a failed one, `downloads-remove-all` clears everything (with
   confirmation).
7. **Share / save.** The viewer and the Downloads overflow offer `action-share` (ACTION_SEND via
   `FileProvider`) and `action-save-copy` (MediaStore Downloads) for a downloaded file.
8. **Originals.** A file with only an original (`hasOriginal && !hasPdf`) offers `action-open-with`
   (`ACTION_VIEW` with the right MIME type); when no app can open it, an explanatory message shows.
9. **Notes reader.** A `note-row-{noteId}` opens `screen-note-reader`, rendering the Markdown
   (`note-markdown`) read-only, with block/inline math (`note-math`) per ADR 0002; RTL-aware, themed,
   cached offline.

## Test-tag contract

Kebab-case, stable tags the reader stories **must** honor. File ids, note ids, page numbers and
course ids are interpolated into the `{…}` segments. #37 (S2) places the viewer tags, #38 (S3) the
open-file/download-action tags, #39 (S4) the Downloads-tab tags, #40 (S5) the share/save tags, #42
(S6) the notes-reader tags. #36 (S1) places no UI tag — it is the engine behind the download/offline
states.

### PDF viewer — placed by #37 (S2)

| Tag | Meaning |
|---|---|
| `screen-pdf-viewer` | Root of the PDF viewer screen. |
| `pdf-page-{n}` | The nth rendered page (1-based); the journey asserts pages 1 and 3. |
| `page-indicator` | The 'n / N' page indicator. |
| `action-jump-to-page` | Jump-to-page control. |
| `viewer-top-bar` | Viewer top bar (hosts the download / share / save actions). |

### Open-file & download action — placed by #38 (S3), driven by the engine #36 (S1)

| Tag | Meaning |
|---|---|
| `viewer-progress` | Determinate stream-to-cache progress shown before the first page. |
| `viewer-download-toggle` | Download / remove-download toggle in the viewer top bar. |
| `download-state-idle` | File not downloaded (the default trailing state). |
| `download-state-running` | Download in progress (with progress). |
| `download-state-done` | Download complete and persisted offline. |
| `download-state-failed` | Download failed (pairs with a retry affordance). |
| `action-open-with` | 'Open with…' route for an original-only file (`hasOriginal && !hasPdf`). |

### Downloads tab — placed by #39 (S4)

| Tag | Meaning |
|---|---|
| `screen-downloads` | Root of the Downloads tab (inherited from E2). |
| `downloads-list` | The downloads list container. |
| `download-group-{courseId}` | A per-course group header. |
| `download-row-{fileId}` | A downloaded / in-progress file row (with size and state). |
| `download-remove` | Remove-this-download action (deletes file + row). |
| `download-retry` | Retry a failed download. |
| `downloads-remove-all` | 'Remove all' action (with confirmation). |
| `storage-usage` | Header showing total storage used by downloads. |

### Share / save — placed by #40 (S5)

| Tag | Meaning |
|---|---|
| `action-share` | Share the downloaded file (`ACTION_SEND` via `FileProvider`). |
| `action-save-copy` | Save a copy to the device's Downloads (MediaStore). |

### Notes reader — placed by #42 (S6)

| Tag | Meaning |
|---|---|
| `screen-note-reader` | Root of the read-only notes reader. |
| `note-markdown` | The rendered Markdown body. |
| `note-math` | A rendered block/inline math node (per ADR 0002, the #41 spike). |

Inherited from earlier epics: `nav-downloads` / `screen-downloads` (E2), `screen-course`,
`course-tabs`, `tab-files` / `tab-notes`, `file-list`, `file-row-{fileId}`, `session-deck-{fileId}`,
`note-row-{noteId}`, `course-card-{slug}` (E4). The reader is reached through those surfaces.

### Reconciliation with #35's draft tag list

The acceptance criteria on #35 named an earlier draft of these tags; this contract is the canonical
one (it aligns with the real E2 `screen-downloads` tab root and spans all of #36–#42). The mapping:
`pdf-viewer` → `screen-pdf-viewer`; `action-download` → `viewer-download-toggle`; `downloads-list`
kept as the list container under `screen-downloads`; `action-remove-download` → `download-remove`;
`storage-used` → `storage-usage`. `page-indicator`, `download-row-{fileId}` and the four
`download-state-*` tags are kept verbatim.

## Cases → owning story

Each `ReaderJourneyTest` case is Ignored with reason `QMOB-E5` and carries a `//` comment naming the
story that un-ignores it.

| `ReaderJourneyTest` case | Un-ignored by |
|---|---|
| `openDeck_rendersFirstPage_withPageIndicator` | QMOB-E5-S2 (#37) |
| `pdfViewer_scrollToThirdPage_rendersThatPage` | QMOB-E5-S2 (#37) |
| `pdfViewer_doubleTap_zoomsPage` | QMOB-E5-S2 (#37) |
| `pdfViewer_reopen_resumesLastReadPage` | QMOB-E5-S2 (#37) |
| `tapFile_streamsContentToCache_thenShowsViewer` | QMOB-E5-S3 (#38) |
| `viewer_toggleDownload_persistsOffline_reachesDoneState` | QMOB-E5-S3 (#38) |
| `downloadedFile_opensWithServerDown` | QMOB-E5-S3 (#38) |
| `originalOnlyFile_offersOpenWith` | QMOB-E5-S3 (#38) |
| `downloadsTab_listsOfflineFile_withSizeAndStorageUse` | QMOB-E5-S4 (#39) |
| `downloadsTab_removeFile_removesRow` | QMOB-E5-S4 (#39) |
| `downloadsTab_retryFailedDownload_restarts` | QMOB-E5-S4 (#39) |
| `viewer_offersShareAndSaveCopy_forDownloadedFile` | QMOB-E5-S5 (#40) |
| `openNote_fromNotesTab_rendersMarkdownBody` | QMOB-E5-S6 (#42) |
| `noteReader_rendersMathBlock` | QMOB-E5-S6 (#42) |

QMOB-E5-S1 (#36, download engine) underpins every download/offline case — the resumable WorkManager
worker + Room `downloads` table behind `download-state-*`, `viewer-progress` and the Downloads tab —
but un-ignores no journey case directly; it is verified by its own unit tests (`DownloadWorkerTest`,
`DownloadRepositoryTest`). QMOB-E5-SP (#41, the Markdown + LaTeX spike) un-ignores nothing either; it
is an ADR that unblocks the `note-math` rendering un-ignored by #42.

## Fixtures

`app/src/androidTest/assets/fixtures/` (served by `MockWebServerRule`). The browse fixtures
(`courses-list.json`, `course-detail.json`, `course-sessions.json`, `course-files.json`,
`course-notes.json`, `error-not-found.json`) are reused from E4 to reach a course's Files/Notes tabs.
The reader adds:

| Fixture | Mirrors | Served as | Used by |
|---|---|---|---|
| `lecture-1.pdf` | `getFileContent.200` (`application/pdf`) | `enqueuePdfFixture` → `200` + `Accept-Ranges`/`ETag`/`Content-Disposition`; `enqueuePdfRange` → `206` + `Content-Range` | viewer, open-file, download, offline, Downloads tab, share/save |
| `reader-note.json` | `getNote.200.json` | `enqueueFixture` (JSON) | notes reader (Markdown + block/inline LaTeX, mixed Hebrew/RTL) |
| `reader-files-original.json` | `listFiles.200.json` (one `hasOriginal && !hasPdf` file) | `enqueueFixture` (JSON) | original-only → `action-open-with` |

`lecture-1.pdf` is a deterministic, dependency-free **5-page** PDF (each page prints its number) so
the viewer can assert a specific `pdf-page-{n}` and the scroll/resume cases have real pages to reach.
`MockWebServerRule.enqueuePdfFixture` / `enqueuePdfRange` serve it with the `mobile-v1` content
headers (strong ETag, `Accept-Ranges: bytes`, `Content-Range` on a 206) so the engine's Range/ETag
resume path (#36) is exercised against the same bytes the viewer renders. Do not hand-edit these to
make a test pass.
