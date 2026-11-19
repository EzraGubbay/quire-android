# E4 — Browse & read (courses / sessions / files / notes): e2e contract

Epic: [QMOB-E4 (#5)](https://github.com/EzraGubbay/quire-android/issues/5) ·
Journey defined first by [QMOB-E4-S0 (#29)](https://github.com/EzraGubbay/quire-android/issues/29).

This is the **contract** the browse stories (#30–#34) build to. The instrumented journey
(`app/src/androidTest/java/com/ezragubbay/quire/e2e/CoursesJourneyTest.kt`) finds UI **by test tag
only** (kebab-case, never by display text — so Hebrew/RTL course names are found the same way as
Latin ones) and runs against `MockWebServer` fixtures whose shapes mirror the real `mobile-v1`
browse contract. The session is seeded signed-in (`SessionSeedRule(signedIn = true)`) pointing at
the hermetic `MockWebServer`. Every case ships Ignored with reason `QMOB-E4`; a story removes that
Ignore from the cases it satisfies. The epic is done when no such Ignore remains in
`CoursesJourneyTest` (a release PR into `main` fails while any remains).

> The literal `@Ignore` annotation text is written **only** in the real annotations in
> `CoursesJourneyTest.kt`. Docs and KDoc describe the convention as "Ignored with reason QMOB-E4" so
> the release-gate grep (over `app/src/androidTest` for the `QMOB`-reason Ignore annotation) matches
> annotations only — the false-match lesson from E2's PR #78/#79.

## The mobile API this talks to

Everything is read-only here and served from the versioned mobile API (`/api/mobile/v1`); every call
carries `Authorization: Bearer <token>`. The data layer (#30) is offline-first — the UI observes Room
and `refresh()` fetches then upserts — so after one successful sync the whole surface works offline.
Shapes are copied from `EzraGubbay/quire` `docs/api/mobile-v1.openapi.yaml` +
`docs/api/fixtures/mobile-v1/`:

| Operation | Success | Notes |
|---|---|---|
| `GET /courses` | `200 [ { slug, id, name, code, semester, type, fileCount } ]` | List for the semester-grouped Courses screen. |
| `GET /courses/{course}` | `200 { …, counts: { sessions, files, notes } }` | Course detail + per-type counts. |
| `GET /courses/{course}/sessions` | `200 [ { kind, number, title, date, fileIds, noteId } ]` | `kind` ∈ `lecture` / `recitation`. |
| `GET /courses/{course}/files` | `200 [ { fileId, title, type, session?, folder?, pageCount, sizeBytes, hasPdf, hasOriginal, hasMarkdown, updatedAt } ]` | `type` ∈ `lecture` / `recitation` / `homework` / `exam` / `book` / `paper` / `other`. |
| `GET /courses/{course}/notes` | `200 [ { id, slug, title, session?, updatedAt } ]` | Note list (read-only; reader is E5). |

The error envelope is `{ "error": { "code", "message" } }` (e.g. `404 not_found` for an unknown
course). File types are the seven server labels; the Files tab groups and filters by them.

## The user journey

1. **Signed-in launch.** The shell starts on Courses (`screen-courses`). `GET /courses` fills a
   `course-list` grouped under sticky semester headers (`course-group-{semester}`, most recent
   first); each `course-card-{slug}` shows name, code and file count. Hebrew course names render
   RTL and are found by their slug tag.
2. **Pull-to-refresh.** A pull on `courses-refresh` issues a second `GET /courses` and upserts the
   cache without clearing the visible list.
3. **Open a course.** Tapping a `course-card-{slug}` opens `screen-course` with a collapsing title
   and a `course-tabs` row — `tab-sessions`, `tab-files`, `tab-notes` — starting on Sessions.
4. **Sessions.** A dated timeline of `session-row-{kind}-{number}` rows (kind `lecture` /
   `recitation`); Lecture 1 shows its deck chip `session-deck-{fileId}` and a note indicator.
   Tapping a deck emits an open-file event (the viewer arrives in E5).
5. **Files.** `tab-files` shows a `file-list` of `file-row-{fileId}` rows, each with a
   `file-type-chip`, page count and size, grouped by type. Multi-select `filter-chip-{type}` chips
   narrow the list (selecting `filter-chip-recitation` leaves only recitation files); `sort-control`
   reorders them.
6. **File info.** Long-press (or overflow) on a `file-row-{fileId}` opens `file-info-sheet` with the
   full title, type, session/folder, pages, size and available formats (PDF / original / Markdown).
7. **Notes.** `tab-notes` lists `note-row-{noteId}` titles from `GET /courses/{course}/notes`
   (cached, read-only); tapping emits an open-note event (reader arrives in E5-S6).
8. **Search.** `action-search` on Courses opens `screen-search`; typing in `search-field` matches
   titles / codes / types over Room (case-insensitive, Hebrew and Latin), sectioned into
   `search-section-courses` and `search-section-files` with `search-result-course-{slug}` /
   `search-result-file-{fileId}` rows. Served from the cache — it works offline.
9. **Offline-first.** With the server down, a cold relaunch still renders the cached `course-list`
   behind an `offline-banner` — everything browsable offline after one successful sync.

## Test-tag contract

Kebab-case, stable tags the browse stories **must** honor. Slugs, semesters, file ids and note ids
are interpolated into the `{…}` segments; `{type}` is one of the seven server labels
(`lecture`, `recitation`, `homework`, `exam`, `book`, `paper`, `other`) and `{kind}` is `lecture`
or `recitation`. #31 (S2) places the Courses-screen tags, #32 (S3) the course-screen/tab tags,
#33 (S4) the Files-tab tags, #34 (S5) the search tags.

### Courses screen — placed by #31 (S2)

| Tag | Meaning |
|---|---|
| `screen-courses` | Root of the Courses screen (shell start destination; inherited from E2). |
| `course-list` | The scrollable, semester-grouped list container. |
| `course-group-{semester}` | Sticky semester header (semester slugified, e.g. `winter-2026`). |
| `course-card-{slug}` | A course card; tapping opens the course. |
| `courses-refresh` | Pull-to-refresh container. |
| `offline-banner` | Unobtrusive banner shown when rendering cached data offline. |
| `action-search` | Top-bar action opening full-screen search (shared with #34). |
| `courses-skeleton` | First-load skeleton (loading state). |
| `courses-empty` | Empty state when the user has no courses. |
| `courses-error` | Error state shown when there is no cache to fall back to. |
| `action-retry` | Retry action inside `courses-error`. |

### Course screen & tabs — placed by #32 (S3)

| Tag | Meaning |
|---|---|
| `screen-course` | Root of the course detail screen. |
| `course-tabs` | The Sessions / Files / Notes tab row. |
| `tab-sessions` / `tab-files` / `tab-notes` | The three tabs. |
| `session-list` | Sessions timeline container. |
| `session-row-{kind}-{number}` | A session row (e.g. `session-row-lecture-1`). |
| `session-deck-{fileId}` | A deck chip inside a session row (tapping emits open-file). |
| `session-note-indicator` | Note indicator inside a session row (when `noteId` is present). |
| `sessions-filter-lectures` / `sessions-filter-recitations` | Lectures / Recitations segmented filter. |
| `note-list` | Notes tab list container. |
| `note-row-{noteId}` | A note row (tapping emits open-note). |

### Files tab — placed by #33 (S4)

| Tag | Meaning |
|---|---|
| `file-list` | Files tab list container. |
| `file-group-{type}` | A per-type group header (Lectures, Recitations, …). |
| `file-row-{fileId}` | A file row. |
| `file-type-chip` | The type-label chip on a file row (one per row). |
| `filter-chip-{type}` | A multi-select filter chip for a file type. |
| `sort-control` | The sort selector (Course order / Title / Recently updated). |
| `file-info-sheet` | Bottom sheet with full file info (title, type, session/folder, pages, size, formats). |

### Search — placed by #34 (S5)

| Tag | Meaning |
|---|---|
| `screen-search` | Root of the full-screen search. |
| `search-field` | The query input (debounced). |
| `search-section-courses` / `search-section-files` | Result section headers. |
| `search-result-course-{slug}` | A course result row. |
| `search-result-file-{fileId}` | A file result row. |
| `search-recent-{n}` | A recent-search entry (shown for an empty query). |

Inherited from E2: `app-root`, `nav-courses` (used to return to Courses so the search action is in
scope). `course-card-{slug}` supersedes E2's placeholder `course-row-{n}`.

## Cases → owning story

Each `CoursesJourneyTest` case is Ignored with reason `QMOB-E4` and carries a `//` comment naming the
story that un-ignores it.

| `CoursesJourneyTest` case | Un-ignored by |
|---|---|
| `signedInLaunch_showsSemesterGroupedCourseList` | QMOB-E4-S2 (#31) |
| `coursesList_pullToRefresh_refetchesFromServer` | QMOB-E4-S2 (#31) |
| `relaunchWithServerDown_showsCachedCourses_withOfflineBanner` | QMOB-E4-S2 (#31) |
| `tapCourseCard_opensCourse_onSessionsTab_showsLectureOneWithDeck` | QMOB-E4-S3 (#32) |
| `courseScreen_exposesSessionsFilesNotesTabs` | QMOB-E4-S3 (#32) |
| `courseScreen_notesTab_listsCachedNotes` | QMOB-E4-S3 (#32) |
| `filesTab_recitationFilterChip_narrowsList` | QMOB-E4-S4 (#33) |
| `filesTab_showsTypeChipsAndSortControl` | QMOB-E4-S4 (#33) |
| `filesTab_openFileInfoSheet_showsFormatsAndSize` | QMOB-E4-S4 (#33) |
| `search_fromCourses_findsFileByTitle` | QMOB-E4-S5 (#34) |

S1 (#30, offline-first data layer) underpins every case — Room cache + repositories feeding the
observed `Flow`s — but un-ignores no journey case directly; it is verified by its own unit/contract
tests (`CourseRepositoryTest`, DAO tests, DTO contract tests).

## Fixtures

`app/src/androidTest/assets/fixtures/` (served by `MockWebServerRule`). Shapes mirror the vendored
`mobile-v1` contract in `EzraGubbay/quire` (`docs/api/fixtures/mobile-v1/`) — do not hand-edit them
to make a test pass.

| Fixture | Mirrors | Used by |
|---|---|---|
| `courses-list.json` | `listCourses.200.json` | list, pull-to-refresh, offline, course-open, search |
| `course-detail.json` | `getCourse.200.json` | course-open, tabs, files, notes, search |
| `course-sessions.json` | `listSessions.200.json` | course-open / sessions |
| `course-files.json` | `listFiles.200.json` | files filter, type chips, file-info, search-by-title |
| `course-notes.json` | `listNotes.200.json` | notes tab |
| `error-not-found.json` | `error.not_found.json` | available for an unknown-course (`404`) case |

`courses-list.json` spans two semesters (`Winter 2026` with two courses — one Hebrew-named — and
`Spring 2026` with one) so grouping is exercised; `course-files.json` carries a lecture, a recitation
and a homework file so the Recitation filter has something to narrow and search has a title
(`Lecture 1 - Convolution`) to find.
