# ADR 0001: Dependency injection strategy

Status: accepted (QMOB-E3-S3, #26)

## Context

Three layers landed independently and ended up with different wiring styles:

- **Network (#24)** was built with Hilt, as its acceptance criteria required (`NetworkModule` provides
  `QuireClient`, `TokenCache`, `SessionEvents`). It consumes two seams it does not implement:
  `TokenProvider` and `ServerConfigProvider`, both synchronous because they run on OkHttp's IO threads.
- **Settings (E2)** and **Session storage (#25)** were written without Hilt: plain constructors, with
  `SettingsViewModel` using a hand-written `ViewModelProvider.Factory`. `SessionRepository` exposes suspend
  functions (`token()`, `current()`, `signIn()`, `signOut()`).

Until this story `MainActivity` was not a Hilt entry point, so Dagger never validated the graph and the two
seams had no bindings. Making `MainActivity` an `@AndroidEntryPoint` forces the whole graph to be complete.

## Decision

1. **Hilt is the DI framework for the app.** New code uses `@Inject` constructors, `@HiltViewModel` and modules
   under `di/`.
2. **`di/SessionModule` is the integration point.** It provides `SessionRepository` (from
   `Context.sessionDataStore`, `KeystoreCipher` and an `@ApplicationScope` coroutine scope) and binds the two
   network seams to it:
   - `TokenProvider { pending.token ?: runBlocking { sessions.token() } }`. This is the suspend-to-sync bridge.
     It runs on OkHttp IO threads, never main, and the singleton `TokenCache` in front of it means only the first
     request after launch or sign-in blocks.
   - `ServerConfigProvider` reads `sessions.state.value` (`SignedIn.serverUrl`) synchronously, with a one-off
     authoritative read if the state has not been restored from disk yet.
3. **`PendingPairing`** holds the server URL and minted token in memory for the duration of a pairing attempt, so
   `POST /auth/pair` and the validating `GET /me` work before anything is persisted. An unverified token never
   reaches disk.
4. **Settings stays manual for now.** `SettingsViewModel` keeps its factory; it is a leaf with no cross-layer
   dependencies and migrating it is churn unrelated to auth.
5. ViewModels are created by `by viewModels()` in the `@AndroidEntryPoint` activity and passed down, because the
   project does not depend on `hilt-navigation-compose` (a nav-entry-scoped `hiltViewModel()` would need it).

## Consequences

- One graph, validated by Dagger at build time; instrumented tests use `@HiltAndroidTest` and seed the session
  through `SessionSeedRule`.
- The `runBlocking` bridge is a deliberate, bounded compromise. If the network layer ever needs a truly
  non-blocking token (for example a refresh flow), replace it with an in-memory token holder that the repository
  updates on every change, instead of blocking.
- Robolectric component and screenshot tests do not touch the graph (they render composables directly), so they
  need no Hilt configuration.

## Plan for consistency

- Migrate `SettingsRepository`/`SettingsViewModel` to Hilt when Settings next changes substantially (the E3-S5
  account section is the natural moment), moving its provisioning into `di/` and deleting the factory.
- Add `hilt-navigation-compose` once a feature needs nav-entry-scoped ViewModels, then drop the activity-level
  ViewModel plumbing for those screens.
- Every new repository gets an `@Inject` constructor or a `di/` provider from day one; no new hand-rolled
  factories.
