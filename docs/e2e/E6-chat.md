# E6 — AI chat (streaming) e2e contract

Epic **QMOB-E6** (#7). The journey is written before any E6 story is implemented (S0, #43) as
instrumented cases in `app/src/androidTest/java/com/ezragubbay/quire/e2e/ChatJourneyTest.kt`, each
ignored with the epic reason and un-ignored by the story (#44–#49) that satisfies it. The epic is
done when no such ignore remains; a release PR into `main` fails while any `QMOB-…` ignore exists.

UI is found by **test tag only** (never display text) — RTL-safe for Hebrew thread titles and for
LaTeX in answers. Every case awaits a **content** node (a populated `thread-list`, the settled
`answer-markdown`, a rendered `pdf-page-{n}`), never a loading-time root, so the cases do not flake at
the gate; streaming cases `waitUntil` the settled answer.

## User journey

1. Open the global **Chats** tab → a list of recent threads across all courses, newest first.
2. From a course screen, **Ask** opens that course's threads; **New chat** creates a thread (scoped to
   a course) and drops into an empty conversation.
3. In a conversation, type a question and **Send**: the user bubble appears (`user` event), then the
   assistant answer grows incrementally as NDJSON `delta` events stream, settling on `done`.
4. **Stop** cancels an in-flight answer, keeping the partial text.
5. Assistant answers render **Markdown** and **block math** (the ADR 0002 Markwon approach, shared
   with the notes reader) as their own taggable nodes.
6. A `[n]` **citation chip** in the answer opens the cited source in the **PDF viewer at the cited
   page** through the open-file flow; a **Sources** row under the answer lists the cited items.
7. Failures are shown in plain words with the right action: a **mid-stream error** keeps the partial
   text and offers **Retry** (retry sends `{ retry: true }`, no duplicate user bubble, and succeeds);
   **budget reached** is terminal (no Retry); **503 `ai_unavailable`** (AI not configured) is shown;
   **offline** disables the composer behind a banner and the cached thread list stays readable.
8. From the viewer, **Ask about this document** opens the document-scoped chat, reusing the most recent
   document thread (via `?documentId=`) instead of creating a duplicate; the conversation header names
   the document, and document threads carry a badge in thread lists.

## Mobile API chat contract (mirrored by the fixtures)

Shapes mirror the real `mobile-v1` chat contract merged in E1 (`docs/api/mobile-v1.openapi.yaml` and
`docs/api/fixtures/mobile-v1/` in `EzraGubbay/quire`). Citations carry **no** web-only `href`.

| Endpoint | Fixture |
|---|---|
| `GET /threads` (recent, across courses) | `chat-threads-recent.json` |
| `GET /courses/{course}/threads` | `chat-threads-course.json` |
| `GET /courses/{course}/threads?documentId=` | `chat-threads-document.json` |
| `POST /courses/{course}/threads` → `201` | `chat-thread-created.json` |
| `GET /courses/{course}/threads/{id}/messages` (empty) | `chat-messages-empty.json` |
| `GET /courses/{course}/threads/{id}/messages` (history) | `chat-messages-history.json` |
| `POST .../messages` → NDJSON, normal | `chat-stream-normal.ndjson` |
| `POST .../messages` → NDJSON, Markdown + block math | `chat-stream-markdown.ndjson` |
| `POST .../messages` → NDJSON, mid-stream `error` | `chat-stream-error-midstream.ndjson` |
| `POST .../messages` → NDJSON, budget `error` (terminal) | `chat-stream-error-budget.ndjson` |
| `POST .../messages` → `503` envelope | `error-ai-unavailable.json` |
| `POST .../messages` → `429` envelope | `error-rate-limited.json` (shared) |

The streaming POST body is `application/x-ndjson`: one JSON event per line, `type: user | delta |
done | error`. `user` is first, then zero or more `delta` (`{ text }`), then `done`
(`{ message }`) or `error` (`{ code, message, kind }`). A mid-stream `error` keeps HTTP `200`; a
pre-stream failure uses the JSON error envelope `{ error: { code, message } }` with a 4xx/5xx status.
`MockWebServerRule.enqueueNdjsonFixture` serves these chunked and throttled so `delta`s arrive
incrementally (the UI must render the answer as it grows, not in one atomic update).

## Test-tag contract (kebab-case, stable)

| Tag | Meaning |
|---|---|
| `screen-chats` | Global Chats tab root (defined in E2; now gets real content) |
| `nav-chats` | Chats bottom-nav destination (E2) |
| `thread-list` | Thread-list container (global Chats tab and a course's threads) |
| `thread-row-{id}` | A thread row; `{id}` is the thread id |
| `thread-doc-badge` | Badge marking a document-scoped thread in the list |
| `action-ask` | Course screen 'Ask' entry point → that course's threads |
| `action-new-thread` | 'New chat' — create a thread and open the conversation |
| `chat-offline-banner` | Read-only offline banner (list stays cached; composer disabled) |
| `screen-conversation` | Conversation screen root |
| `conversation-doc-header` | Header naming the document for a document-scoped chat |
| `message-user-{i}` | A user message bubble; `{i}` is its index in the conversation |
| `message-assistant-{i}` | An assistant message bubble; `{i}` is its index |
| `answer-markdown` | The assistant answer's rendered Markdown body (settled-answer node) |
| `answer-math` | A block-LaTeX node inside an assistant answer (ADR 0002, as `note-math`) |
| `chat-input` | The composer text field |
| `action-send` | Send the question (disabled when blank or while streaming) |
| `action-stop` | Stop the in-flight answer (keeps the partial) |
| `citation-chip-{n}` | Inline tappable `[n]` citation chip; opens the source at its page |
| `sources-row` | The Sources row under an answer |
| `source-row-{n}` | A cited source entry (title + page) in the Sources row |
| `chat-error` | An error state in the conversation (plain-words message) |
| `action-retry` | Retry a retryable failure (absent for terminal `budget`) |
| `action-ask-about-doc` | Viewer top-bar 'Ask about this document' |

Reused from other epics within the journey (their own contracts own these): `course-card-{slug}`,
`screen-course`, `tab-files`, `file-list`, `file-row-{fileId}` (E4); `screen-pdf-viewer`,
`pdf-page-{n}` (E5) — a citation lands on `pdf-page-7`.

### Reconciliation with the #43 draft tag names

The #43 acceptance-criteria draft listed a few tags under earlier names; the canonical, stable names
above supersede them (one contract, no aliases in code):

| #43 draft | Canonical |
|---|---|
| `chat-thread-list` | `thread-list` |
| `citation-{n}` | `citation-chip-{n}` |
| `action-ask-document` | `action-ask-about-doc` |
| `message-user-{i}`, `message-assistant-{i}` | unchanged |
| `thread-row-{id}`, `action-new-thread`, `chat-input`, `action-send`, `action-stop`, `chat-error`, `action-retry` | unchanged |

## Cases → owning story (removes the ignore)

| Case | Story |
|---|---|
| `chatsTab_listsRecentThreads` | #45 (S2) |
| `courseScreen_askEntryPoint_opensThatCoursesThreads` | #45 (S2) |
| `chatsTab_newThread_opensEmptyConversation` | #45 (S2) |
| `chatsTab_offline_showsCachedListWithBanner` | #45 (S2) |
| `conversation_send_showsUserBubble_thenStreamsAnswerIncrementally` | #46 (S3) |
| `conversation_stopGeneration_keepsPartialAnswer` | #46 (S3) |
| `conversation_rendersMarkdownAndMath` | #46 (S3) |
| `answer_citationChip_opensViewerAtCitedPage` | #47 (S4) |
| `answer_sourcesRow_listsCitedSource` | #47 (S4) |
| `stream_errorMidStream_keepsPartial_showsRetry_retrySucceeds` | #48 (S5) |
| `stream_budgetReached_showsError_withoutRetry` | #48 (S5) |
| `preStream_aiUnavailable_showsError` | #48 (S5) |
| `conversation_offline_disablesComposer_showsBanner` | #48 (S5) |
| `viewer_askAboutDocument_opensDocumentScopedChat` | #49 (S6) — closes the epic |
| `threadList_documentThread_carriesDocumentBadge` | #49 (S6) |

The chat data layer (#44, S1: NDJSON → `Flow<ChatEvent>`, Room-cached threads/messages) underpins
every streaming and thread case but removes no ignore directly; it is verified by its own unit tests
(`NdjsonParserTest`, `ChatRepositoryTest`).
