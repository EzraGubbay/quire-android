# E3 — Auth (device pairing): e2e contract

Epic: [QMOB-E3 (#4)](https://github.com/EzraGubbay/quire-android/issues/4) ·
Journey defined first by [QMOB-E3-S0 (#23)](https://github.com/EzraGubbay/quire-android/issues/23).

This is the **contract** the auth stories (#24–#28) build to. The instrumented journey
(`app/src/androidTest/java/com/ezragubbay/quire/e2e/AuthJourneyTest.kt`) finds UI **by test tag
only** (kebab-case, never by display text) and runs against `MockWebServer` fixtures whose shapes
mirror the real `mobile-v1` pairing contract. Every case ships Ignored with reason `QMOB-E3`; a
story removes that Ignore from the cases it satisfies. The epic is done when no such Ignore remains
in `AuthJourneyTest` (a release PR into `main` fails while any remains).

> The literal `@Ignore` annotation text is written **only** in the real annotations in
> `AuthJourneyTest.kt`. Docs and KDoc describe the convention as "Ignored with reason QMOB-E3" so
> the release-gate grep (over `app/src/androidTest` for the `QMOB`-reason Ignore annotation) matches
> annotations only — the false-match lesson from PR #78/#79.

## The mobile API this talks to

The app never mints pairing codes; the web app does. The user opens the web Settings page
(session-authenticated `POST /api/mobile/pairing-codes`, outside v1) to get a short single-use code
(and a `quire://pair?server=…&code=…` QR). The app then exchanges it on the versioned mobile API
(`/api/mobile/v1`). The journey exercises three operations, with shapes copied from
`EzraGubbay/quire` `docs/api/mobile-v1.openapi.yaml` + `docs/api/fixtures/mobile-v1/`:

| Operation | Request | Success | Failure |
|---|---|---|---|
| `POST /auth/pair` (no auth) | `{ "code": "AbC12345", "deviceName": "Pixel 9" }` | `200 { "token", "device": { "id", "name" } }` | `400 invalid_request`, `429 rate_limited`, `500 internal` |
| `GET /me` (bearer) | — | `200 { "login", "apiVersion", "device" }` | `401 unauthorized` |
| `DELETE /auth/token` (bearer) | — | `204` (no body) | `401 unauthorized` |

Every authenticated call carries `Authorization: Bearer <token>`; a `401` on an authenticated call
is the `SessionExpired` signal that routes back to Pair. The error envelope is
`{ "error": { "code", "message" } }`.

## The user journey

1. **Fresh install.** With no session, launch shows the **Pair** screen (`screen-pair`), *not* the
   tab shell (`app-root` is absent). The form exposes `field-server-url` (`https://` prefilled,
   validated), `field-pair-code` (8 chars, auto-uppercase, paste-friendly), `field-device-name`
   (prefilled from `Build.MODEL`, editable), `action-pair`, and `action-scan-qr`.
2. **Pair.** Enter the server URL and code and tap `action-pair`. `POST /auth/pair` returns `200`;
   the token is stored (encrypted) and the app navigates to the shell at Courses (`screen-courses`),
   clearing the back stack.
3. **Stay signed in.** A relaunch restores the session (validated via `GET /me`) and lands straight
   on Courses — no re-pairing; `screen-pair` is absent.
4. **Bad code.** An invalid or expired code (`400 invalid_request`) shows an inline `pair-error` and
   stays on `screen-pair`; the shell is not shown.
5. **Deep link.** A `quire://pair?server=…&code=…` URI (scanned QR or opened as a deep link) lands
   on `screen-pair` with `field-server-url` and `field-pair-code` pre-filled.
6. **Sign out.** From the shell, `action-settings` opens Settings; the `settings-account` section
   shows server/login/device; `action-sign-out` calls `DELETE /auth/token` (best-effort), clears the
   token and returns to `screen-pair`. A subsequent relaunch stays on Pair.
7. **Expired session.** A revoked/expired token yields `401` on the next authenticated call; the
   session is cleared and the app returns to `screen-pair` with a `pair-error` message.

## Test-tag contract

Kebab-case tags the auth stories **must** honor. #26 (S3) places the Pair-form and error tags; #27
(S4) places `action-scan-qr` and wires the deep link; #28 (S5) places the Settings account tags.

### Pair screen — `screen-pair`, `field-*`, `action-pair`, `action-scan-qr`, `pair-error`

| Tag | Meaning | Placed by |
|---|---|---|
| `screen-pair` | Root of the Pair screen (shown instead of the tab shell when signed out). | #26 |
| `field-server-url` | Server URL text field (validated, `https://` prefilled). | #26 |
| `field-pair-code` | Pairing-code text field (8 chars, auto-uppercase). | #26 |
| `field-device-name` | Device-name text field (prefilled from `Build.MODEL`, editable). | #26 |
| `action-pair` | Pair button (has a loading state). | #26 |
| `action-scan-qr` | Button that opens the QR scanner. | #27 |
| `pair-error` | Inline error message on the Pair screen (bad/expired code, rate limited, unreachable, not a Quire server, or a `401`-driven "signed out — pair again"). | #26 |

### Settings → Account — `settings-account`, `action-sign-out`

| Tag | Meaning | Placed by |
|---|---|---|
| `settings-account` | Account section in Settings (server URL, login, device name). | #28 |
| `action-sign-out` | Sign-out action in the Account section. | #28 |

Inherited from E2: `action-settings` (top-bar action that pushes Settings), `app-root`,
`screen-courses` (used to assert the signed-in shell is shown / not shown).

## Cases → owning story

Each `AuthJourneyTest` case is Ignored with reason `QMOB-E3` and carries a `//` comment naming the
story that un-ignores it.

| `AuthJourneyTest` case | Un-ignored by |
|---|---|
| `freshInstall_showsPairScreen_notTabShell` | QMOB-E3-S3 (#26) |
| `enterServerUrlAndCode_pairs_landsOnCourses` | QMOB-E3-S3 (#26) |
| `relaunch_afterPairing_staysSignedIn` | QMOB-E3-S3 (#26) |
| `invalidOrExpiredCode_showsError_staysOnPair` | QMOB-E3-S3 (#26) |
| `quirePairDeepLink_prefillsServerAndCode` | QMOB-E3-S4 (#27) |
| `signOut_fromSettings_returnsToPair_andClearsToken` | QMOB-E3-S5 (#28) |
| `expiredSession_onRelaunch_returnsToPairWithMessage` | QMOB-E3-S5 (#28) |

S1 (#24, network core) and S2 (#25, secure session storage) underpin every case — the typed client,
auth interceptor, `ApiResult` mapping and Keystore-encrypted session are what the journey drives —
but they un-ignore no journey case directly; they are verified by their own unit/instrumented tests
(`AuthInterceptorTest`, `ApiResultMappingTest`, `ContractFixturesTest`, `SessionRepositoryTest`,
`KeystoreCipherTest`).

## Fixtures

`app/src/androidTest/assets/fixtures/` (served by `MockWebServerRule`). Shapes mirror the vendored
`mobile-v1` contract in `EzraGubbay/quire` (`docs/api/fixtures/mobile-v1/`) — do not hand-edit them
to make a test pass.

| Fixture | Mirrors | Used by |
|---|---|---|
| `pair-success.json` | `pairDevice.200.json` | pair, relaunch, sign-out, expired-session setup |
| `me.json` | `getMe.200.json` | relaunch (`GET /me` validation) |
| `error-invalid-code.json` | `error.invalid_request.json` | invalid/expired code (`400`) |
| `error-unauthorized.json` | `error.unauthorized.json` | expired session (`401`) |
| `error-rate-limited.json` | `error.rate_limited.json` | available for the S3 "rate limited" inline-error case |

`DELETE /auth/token` returns `204` with no body, so the sign-out case enqueues a bare
`MockResponse().setResponseCode(204)` rather than a fixture.
