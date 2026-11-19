# E2 — App shell: e2e contract

Epic: [QMOB-E2 (#3)](https://github.com/EzraGubbay/quire-android/issues/3) ·
Journey defined first by [QMOB-E2-S0 (#16)](https://github.com/EzraGubbay/quire-android/issues/16).

This is the **contract** the shell stories (#17–#22) build to. The instrumented journey
(`app/src/androidTest/java/com/ezragubbay/quire/e2e/ShellJourneyTest.kt`) finds UI **by test tag only**
(kebab-case, never by display text) and runs against `MockWebServer` fixtures. Every case ships
`@Ignore("QMOB-E2")`; a story removes the `@Ignore` from the cases it satisfies. The epic is done when no
`@Ignore("QMOB-E2")` remains in `ShellJourneyTest`.

## The user journey

1. **Launch.** The app opens on the shell. `app-root` is shown. The start destination is **Courses**:
   `screen-courses` is shown and `nav-courses` is the selected tab.
2. **Switch tabs.** Tapping `nav-downloads` shows `screen-downloads`; tapping `nav-chats` shows `screen-chats`;
   tapping `nav-courses` returns to `screen-courses`. The tapped tab is the selected one each time.
3. **State is kept.** After scrolling the Courses list, switching to another tab and back leaves the Courses
   list where it was (the row scrolled into view is still shown without re-scrolling) — each tab keeps its own
   back stack and scroll state.
4. **System back.** From a non-start tab, system back returns to **Courses** (it does not exit). From Courses
   (the start destination), system back exits the app (the activity finishes).
5. **Settings.** Settings opens from a top-bar action (`action-settings`) as a pushed screen (`screen-settings`);
   system back from Settings returns to the shell.
6. **Change theme.** In Settings the appearance options `setting-theme-system`, `setting-theme-light`,
   `setting-theme-dark` are present and selectable; choosing one selects it and applies immediately (no restart).
7. **Persistence.** The chosen theme survives an activity relaunch: after recreate, re-opening Settings shows the
   previously chosen option still selected.

## Test-tag contract

Kebab-case tags the shell stories **must** honor. S2 (#18) places the placeholder screens' tags; S5 (#21) places
the theme-option tags.

### Bottom navigation (destinations) — `nav-*`

| Tag | Meaning |
|---|---|
| `nav-courses` | Courses tab item in the `NavigationBar` (start destination). |
| `nav-downloads` | Downloads tab item. |
| `nav-chats` | Chats tab item. |

### Top-level screens — `screen-*`

| Tag | Meaning |
|---|---|
| `screen-courses` | Root of the Courses destination content. |
| `screen-downloads` | Root of the Downloads destination content. |
| `screen-chats` | Root of the Chats destination content. |
| `screen-settings` | Root of the pushed Settings screen. |

### Actions and settings controls

| Tag | Meaning |
|---|---|
| `action-settings` | Top-bar action that pushes the Settings screen. |
| `setting-theme-system` | Appearance option "System" (follow device). |
| `setting-theme-light` | Appearance option "Light". |
| `setting-theme-dark` | Appearance option "Dark". |

### Supporting tag (scroll/state case only)

| Tag | Meaning |
|---|---|
| `course-row-{n}` | The n-th row in the Courses list placeholder, used only to assert scroll-state preservation. The Courses placeholder exposes a scrollable list of at least 20 tagged rows (`course-row-0` … `course-row-19`). |

Inherited from E0: `app-root` (the shell root, placed by the smoke test's contract).

## Cases → owning story

Each `ShellJourneyTest` case carries `@Ignore("QMOB-E2")` and a comment naming the story that un-ignores it.

| `ShellJourneyTest` case | Un-ignored by |
|---|---|
| `launch_startsOnCoursesDestination` | QMOB-E2-S2 (#18) |
| `switchingTabs_showsEachTopLevelDestination` | QMOB-E2-S2 (#18) |
| `switchingTabsAndBack_keepsCoursesScrollState` | QMOB-E2-S2 (#18) |
| `systemBack_fromOtherTab_returnsToCourses` | QMOB-E2-S2 (#18) |
| `systemBack_fromCourses_exitsApp` | QMOB-E2-S2 (#18) |
| `topBarAction_opensSettings` | QMOB-E2-S2 (#18) |
| `systemBack_fromSettings_returnsToShell` | QMOB-E2-S2 (#18) |
| `settings_exposesThemeOptions` | QMOB-E2-S5 (#21) |
| `changingTheme_selectsOptionImmediately` | QMOB-E2-S5 (#21) |
| `themeChoice_persistsAcrossRelaunch` | QMOB-E2-S5 (#21) |

S1 (#17 theme tokens) underpins theming but un-ignores no journey case; S3/S4 (components, Roborazzi) and S6 (app
identity) are verified by their own unit/screenshot tests, not by this journey.

## Fixtures

`app/src/androidTest/assets/fixtures/` (served by `MockWebServerRule`). The shell itself renders placeholder
screens and needs no live data; `courses.json` is enqueued so the journey is wired to `MockWebServer` per the
harness contract and is ready for the data stories (E4) to consume. It is a placeholder shape, to be replaced by
the vendored mobile-v1 contract fixture when network lands (E3/E4) — do not treat its shape as the API contract.

| Fixture | Used by |
|---|---|
| `courses.json` | `ShellJourneyTest` setup (Courses list, placeholder). |
