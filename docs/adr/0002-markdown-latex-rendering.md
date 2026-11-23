# ADR 0002: Markdown + LaTeX rendering for notes and chat answers

Status: accepted (QMOB-E5-SP, #41)

> Naming note: the E5 contract (`docs/e2e/E5-reader.md`) and `ReaderJourneyTest` refer to this
> spike's output as "ADR 0001 (the #41 spike)". It ships as **0002** because `0001` is already the
> DI ADR. The `note-math` guidance those references point to is this document.

## Context

Course notes and AI chat answers are **math-heavy** and routinely **mix Hebrew (RTL) with inline
and block LaTeX** inside the same paragraph. The mobile API hands the app raw Markdown
(`GET /courses/{course}/notes/{noteId}` → `{ …, markdown }`); the reader renders it **read-only**,
offline-first, themed light/dark, in the reading face (Literata). The representative fixture
(`app/src/androidTest/assets/fixtures/reader-note.json`) is one real-shaped note: an H1, a Hebrew+Latin
paragraph (`…in Hebrew קונבולוציה:`), a block equation `$$ (f * g)(t) = \int … d\tau $$`, an inline
equation `$e^{i\pi} + 1 = 0$`, and a wiki-link `[[lecture-2-notes]]`.

Two things constrain the choice hard:

- **AGENTS.md — Stack:** "Native only: Kotlin + Jetpack Compose. No React Native, Flutter, KMP,
  **WebView-wrapped web UI.**" A bundled-browser renderer is off the table as the primary approach.
- **The e2e test-tag contract** (`docs/e2e/E5-reader.md`): the journey finds UI **by test tag only**,
  never by display text (so Hebrew/RTL and formulae are found like Latin). `ReaderJourneyTest`
  asserts a body node `note-markdown` **and** a displayed math node `note-math`
  (`noteReader_rendersMathBlock` → `onNodeWithTag("note-math").assertIsDisplayed()`). Whatever we
  pick, a **block equation must surface as its own displayed, taggable node.**

Axes to weigh: RTL + mixed-direction correctness · offline use · incremental updates while a chat
answer streams (E6) · performance on a long note · APK size · theming (light/dark + Literata) ·
accessibility (TalkBack) · testability against the tag contract · maintenance/footprint.

Scope (per #41): decide and document. Out of scope: shipping the renderer, editing notes, and
wiki-link resolution beyond listing it as a follow-up.

## Options

### (a) Markwon + JLatexMath plugin (AndroidView) — **chosen**

`io.noties.markwon:core` renders Markdown to a `Spanned` on a platform `TextView`; the `ext-latex`
plugin renders each formula with **JLatexMath** (`ru.noties:jlatexmath-android`, a pure-Java port of
JLaTeXMath) to a `Drawable` drawn inline — **no WebView, no JS, fully offline**, math fonts bundled
in the APK.

- **RTL:** best available. The platform `TextView` runs Android's Unicode BiDi algorithm, so mixed
  Hebrew/Latin paragraphs and mirrored block layout are handled by the OS, not by us. Equations stay
  LTR inside an RTL paragraph, which is correct typographic behaviour.
- **Math fidelity:** high for the AMS subset we need (integrals, fractions, `e^{i\pi}`, Greek, limits).
  Rendered as an image, so a formula does not reflow or select; its colour must be set explicitly to
  track the theme. JLaTeXMath uses its own math fonts (not Literata) — standard and expected for math.
- **Offline / size:** self-contained; adds the Markwon core/ext-latex classes plus JLatexMath fonts
  (low-single-digit MB, shrinkable by R8). No network, no assets to host.
- **Streaming (E6):** re-render the growing string on each chunk (`Markwon.setMarkdown(tv, text)`),
  debounced; acceptable for a chat bubble. Not incremental DOM, but cheap enough at note/answer size.
- **Testability:** see Decision — the note is **segmented into blocks**, so a block equation is its
  own composable we tag `note-math`; the body `Column` is `note-markdown`. Clean fit to the contract.
- **Accessibility:** `TextView` + TalkBack is solid; math drawables get a `contentDescription`
  (the LaTeX source) so the equation is announced.
- **Maintenance:** Markwon is **stable but dormant** (last release 4.6.2, ~3 years old). It is small,
  dependency-light and self-contained; the risk is "unmaintained", not "unstable". Mitigated by the
  segmentation seam (below), which localises any future swap of the prose or math engine.

### (b) Compose-native Markdown renderer (e.g. `com.mikepenz:multiplatform-markdown-renderer`) + a math solution

Renders Markdown with Compose `Text`/layout nodes directly — inherits Compose typography (Literata),
colour and `LocalLayoutDirection` for free, and gives natural per-node test tags.

- **Math is the blocker.** There is no mature pure-Compose LaTeX renderer. mikepenz's LaTeX support
  exists only in an **unmerged PR** with partial platform coverage (Android via a native glyph
  drawer; JVM/JS stubbed) and **no stated RTL handling**. In practice you would still fall back to
  JLaTeXMath (an AndroidView image) for the math nodes — i.e. you inherit option (a)'s math approach
  **and** carry a second, actively-moving Markdown engine.
- **RTL** for block-level Markdown (nested lists, block-quotes, mirrored punctuation) is less
  battle-tested in these libs than the platform `TextView`'s BiDi.
- The one genuine win — natural test tags — we get anyway via segmentation, without a second engine.

### (c) WebView with bundled KaTeX/MathJax (offline)

Markdown→HTML + KaTeX, all assets under `file:///android_asset/`, no network.

- **Best math fidelity** (KaTeX is the gold standard and exactly matches the Quire web app) and the
  **easiest streaming** (append to the DOM; KaTeX auto-render). HTML/CSS `dir="auto"` gives excellent
  mixed RTL.
- **Ruled out by AGENTS.md** ("no WebView-wrapped web UI"). Independently costly: WebView startup and
  memory per note, a JS execution surface even for local assets, KaTeX fonts/JS in the APK, weaker
  and inconsistent TalkBack, and — decisively for the contract — the DOM is **opaque to Compose
  semantics**, so `onNodeWithTag("note-math").assertIsDisplayed()` cannot see a formula inside the
  WebView without a wrapper-level hack. Its advantages (web parity, trivial streaming) do not
  outweigh the ban for read-only content that Markwon renders natively.

| | (a) Markwon + JLatexMath | (b) Compose-native + math | (c) WebView + KaTeX |
|---|---|---|---|
| AGENTS.md "no WebView" | ✅ native | ✅ native | ❌ **violates** |
| Offline | ✅ bundled | ✅ (math still needs JLaTeXMath) | ✅ bundled assets |
| RTL / mixed-direction | ✅ platform BiDi (strongest) | ⚠️ less proven at block level | ✅ CSS `dir=auto` |
| Math fidelity | ✅ AMS subset (image) | ⚠️ immature / unmerged | ✅✅ KaTeX (web parity) |
| Streaming (E6) | ⚠️ re-render, debounced | ⚠️ re-render | ✅ incremental DOM |
| `note-math` taggable node | ✅ via segmentation | ✅ native + segmentation | ❌ opaque DOM |
| Theming (dark + Literata) | ✅ feed colour + typeface | ✅✅ inherits theme | ⚠️ CSS bridge |
| Accessibility (TalkBack) | ✅ TextView + desc | ✅ Compose semantics | ⚠️ inconsistent |
| APK footprint | low-single-digit MB | 2 engines | fonts + JS |
| Maintenance | ⚠️ dormant but stable | ⚠️ moving, math in flux | ⚠️ KaTeX upkeep + JS surface |

## Decision

**Adopt (a): Markwon core + the Markwon JLatexMath (`ext-latex`) plugin, rendered through
`AndroidView`, with the note content segmented into blocks so each block is its own tagged
composable.**

Rationale: it is the only option that is fully offline, respects the AGENTS.md "no WebView" rule,
gives the **strongest RTL** (platform BiDi for the Hebrew/Latin/formula mix), renders high-fidelity
math with **zero JS/network surface and a small footprint**, and — via segmentation — satisfies the
`note-markdown`/`note-math` tag contract cleanly while keeping TalkBack working. (b) and (c) each win
on a single axis (theme inheritance; web-parity streaming) that does not pay for their costs here:
(b) still needs JLaTeXMath for the math and adds a second engine; (c) is banned and opaque to the
test tags.

**Segmentation is the architectural seam.** The reader splits the raw Markdown into an ordered list
of blocks — prose blocks vs. block-math blocks (`$$ … $$`) — before rendering:

- The reader root `Column` carries `Modifier.testTag("note-markdown")`.
- Each **prose block** is one `AndroidView` hosting a Markwon-rendered `TextView` (inline `$…$` math
  stays inside it as an inline image span; platform BiDi handles inline Hebrew + formula).
- Each **block-math** block is one `AndroidView` hosting a JLaTeXMath view, carrying
  `Modifier.testTag("note-math")` — this is the node `noteReader_rendersMathBlock` asserts.

The seam is what makes Markwon's dormancy acceptable: the prose engine and the math engine sit behind
a per-block boundary, so either can be replaced later (e.g. a Compose-native prose renderer, or a
future Compose LaTeX lib) without touching the tag contract or the rest of the reader.

## Consequences

- Two bundled dependencies (Markwon core + ext-latex, the latter pulling `jlatexmath-android`); math
  fonts ship in the APK. No network, no hosted assets, no JS.
- Math renders as themed images: colour and text size must be fed from `MaterialTheme` on every theme
  change (light/dark), and formulae do not reflow or allow text selection. Accepted for read-only.
- Streaming chat answers (E6) re-render on each chunk (debounced), not incrementally. Revisit only if
  profiling on a real device shows jank on long streamed answers.
- Markwon is dormant; the block-segmentation seam is the exit if it ever needs replacing.
- Markwon's ext-latex only knows `$$…$$` (inline inside a line, block on its own lines); the server writes `$…$`
  inline and the model sometimes `\(…\)` / `\[…\]`. `mathMarkdownBlocks` (`feature/notes/MathDelimiters.kt`) ports the
  web app's display-math normalisation and rewrites inline `$…$` / `\(…\)` to `$$…$$` before rendering, outside code.
- JLaTeXMath resolves many built-in macros (`\dfrac`, `cases`, `pmatrix`, …) by class/method name, so
  `proguard-rules.pro` keeps `org.scilab.forge.jlatexmath.**` whole; without it those formulas fail in release builds.

## Follow-ups to unblock #42 (notes reader, E5-S6) and E6 (chat answers)

**Catalog (add in #42, not in this docs-only spike).** This ADR adds **no** dependency; keep the gate
green by adding these when the reader actually needs them:

```toml
# [versions]
markwon = "4.6.2"
# [libraries]
markwon-core     = { module = "io.noties.markwon:core",      version.ref = "markwon" }
markwon-ext-latex = { module = "io.noties.markwon:ext-latex", version.ref = "markwon" }
```

`ext-latex` transitively pulls `ru.noties:jlatexmath-android` (offline math fonts) — do not add it
separately. Note the v4 ext-latex API change: inline LaTeX is **opt-in** and must be enabled on the
plugin builder; block math `$$ … $$` is the default path.

**Concrete acceptance guidance for #42:**

1. **Library:** Markwon `4.6.2` (`core` + `ext-latex`), rendered via `AndroidView`. No WebView.
2. **Test tags:** segment the Markdown into blocks; root `Column` → `note-markdown`; each block-math
   composable → `note-math`. Inline `$…$` renders inside its prose `TextView` (no separate tag). This
   un-ignores `openNote_fromNotesTab_rendersMarkdownBody` and `noteReader_rendersMathBlock`. Follow
   the E5 robust-wait rule: place `note-markdown` only once content is actually on screen (not while
   Loading), so later cases do not flake at the CI gate.
3. **RTL:** do not force a direction — let the platform `TextView` BiDi resolve mixed Hebrew/Latin.
   Set the reader container to `dir=auto` semantics by leaving `LocalLayoutDirection` untouched; block
   equations stay LTR. Add a Roborazzi screenshot of the Hebrew+math fixture in both directions.
4. **Dark mode + font:** feed Markwon the `onSurface` colour and `bodyLarge`/Literata typeface from
   `MaterialTheme` (a `MarkwonConfiguration`/`TextView` typeface + colour), and set the JLaTeXMath
   colour to `onSurface` so equations recolour with the theme. Re-render (or recompose) on theme
   change. Add light and dark Roborazzi baselines.
5. **Offline assets:** none — Markwon + JLaTeXMath are self-contained; the note Markdown is cached by
   the existing download/cache path. Nothing to host.
6. **Accessibility:** give each math view a `contentDescription` of its LaTeX source so TalkBack
   announces the equation.
7. **Wiki-links (`[[…]]`)** are **out of scope** for #42 beyond rendering the literal text; design
   resolution later. Do not let an unresolved `[[…]]` crash or swallow the paragraph.

**For E6 (AI chat answer rendering, E6-S3):** reuse the same block renderer for streamed answers;
re-render the accumulating Markdown per chunk, debounced (~16–50 ms). Proposed acceptance: a streamed
answer containing a block equation renders progressively without dropping frames on a mid-range
device, and the final frame matches the same content rendered statically (Roborazzi).
