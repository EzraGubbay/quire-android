# Epic e2e contracts

One file per epic (`E<n>.md`) listing the kebab-case test tags and `MockWebServer` fixtures its e2e spec relies on.
See `docs/TESTING.md`.

| Epic | Tags | Fixtures |
|---|---|---|
| E0 | `app-root` | `harness-ping.json` |
| E2 ([E2-shell.md](E2-shell.md)) | `nav-courses`, `nav-downloads`, `nav-chats`, `screen-courses`, `screen-downloads`, `screen-chats`, `screen-settings`, `action-settings`, `setting-theme-system`, `setting-theme-light`, `setting-theme-dark`, `course-row-{n}` | `courses.json` |
| E3 ([E3-auth.md](E3-auth.md)) | `screen-pair`, `field-server-url`, `field-pair-code`, `field-device-name`, `action-pair`, `action-scan-qr`, `pair-error`, `settings-account`, `action-sign-out` | `pair-success.json`, `me.json`, `error-invalid-code.json`, `error-unauthorized.json`, `error-rate-limited.json` |
