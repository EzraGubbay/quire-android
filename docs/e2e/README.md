# Epic e2e contracts

One file per epic (`E<n>.md`) listing the kebab-case test tags and `MockWebServer` fixtures its e2e spec relies on.
See `docs/TESTING.md`.

| Epic | Tags | Fixtures |
|---|---|---|
| E0 | `app-root` | `harness-ping.json` |
| E2 ([E2-shell.md](E2-shell.md)) | `nav-courses`, `nav-downloads`, `nav-chats`, `screen-courses`, `screen-downloads`, `screen-chats`, `screen-settings`, `action-settings`, `setting-theme-system`, `setting-theme-light`, `setting-theme-dark`, `course-row-{n}` | `courses.json` |
| E3 ([E3-auth.md](E3-auth.md)) | `screen-pair`, `field-server-url`, `field-pair-code`, `field-device-name`, `action-pair`, `action-scan-qr`, `pair-error`, `settings-account`, `action-sign-out` | `pair-success.json`, `me.json`, `error-invalid-code.json`, `error-unauthorized.json`, `error-rate-limited.json` |
| E4 ([E4-browse.md](E4-browse.md)) | `screen-courses`, `course-list`, `course-group-{semester}`, `course-card-{slug}`, `courses-refresh`, `offline-banner`, `action-search`, `screen-course`, `course-tabs`, `tab-sessions`, `tab-files`, `tab-notes`, `session-row-{kind}-{number}`, `session-deck-{fileId}`, `note-list`, `note-row-{noteId}`, `file-list`, `file-row-{fileId}`, `file-type-chip`, `filter-chip-{type}`, `sort-control`, `file-info-sheet`, `screen-search`, `search-field`, `search-section-files`, `search-result-file-{fileId}` | `courses-list.json`, `course-detail.json`, `course-sessions.json`, `course-files.json`, `course-notes.json`, `error-not-found.json` |
| E5 ([E5-reader.md](E5-reader.md)) | `screen-pdf-viewer`, `pdf-page-{n}`, `page-indicator`, `action-jump-to-page`, `viewer-top-bar`, `viewer-progress`, `viewer-download-toggle`, `download-state-idle`, `download-state-running`, `download-state-done`, `download-state-failed`, `action-open-with`, `screen-downloads`, `downloads-list`, `download-group-{courseId}`, `download-row-{fileId}`, `download-remove`, `download-retry`, `downloads-remove-all`, `storage-usage`, `action-share`, `action-save-copy`, `screen-note-reader`, `note-markdown`, `note-math` | `lecture-1.pdf`, `reader-note.json`, `reader-files-original.json` (+ E4 browse fixtures) |
