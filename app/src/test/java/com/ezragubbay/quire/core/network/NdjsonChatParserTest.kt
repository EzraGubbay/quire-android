package com.ezragubbay.quire.core.network

import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdjsonChatParserTest {
    private fun parse(text: String): Pair<List<ChatEvent>, Boolean> =
        runBlocking {
            val out = mutableListOf<ChatEvent>()
            val terminal = parseChatStream(Buffer().writeUtf8(text)) { out += it }
            out to terminal
        }

    @Test
    fun normalFixtureYieldsUserDeltasDone() {
        val (events, terminal) = parse(fixture("chat-stream-normal.ndjson"))
        assertTrue(terminal)
        assertEquals(4, events.size)
        assertTrue(events[0] is ChatEvent.User)
        assertEquals("Convolution slides ", (events[1] as ChatEvent.Delta).text)
        val done = events[3] as ChatEvent.Done
        assertEquals("assistant", done.message.role)
        assertEquals(
            7,
            done.message.citations
                .single()
                .pageNo,
        )
    }

    @Test
    fun budgetErrorIsTerminalNotRetryable() {
        val error = parse(fixture("chat-stream-error-budget.ndjson")).first.last() as ChatEvent.Error
        assertEquals(ErrorCode.RateLimited, error.code)
        assertEquals("budget", error.kind)
        assertFalse(error.retryable)
    }

    @Test
    fun midStreamErrorIsRetryableAndKeepsEarlierDeltas() {
        val (events, terminal) = parse(fixture("chat-stream-error-midstream.ndjson"))
        assertTrue(terminal)
        assertTrue(events[1] is ChatEvent.Delta)
        val error = events.last() as ChatEvent.Error
        assertEquals(ErrorCode.AiUnavailable, error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun unknownKindAndMissingKindAreRetryable() {
        val weird = """{"type":"error","code":"internal","message":"x","kind":"weird"}"""
        val e1 = parseChatLine(weird) as ChatEvent.Error
        val e2 = parseChatLine("""{"type":"error","code":"internal","message":"x"}""") as ChatEvent.Error
        assertTrue(e1.retryable && e2.retryable)
        assertNull(e2.kind)
    }

    @Test
    fun codeOutsideEnumMapsToUnknown() {
        val e = parseChatLine("""{"type":"error","code":"brand_new","message":"x"}""") as ChatEvent.Error
        assertEquals(ErrorCode.Unknown, e.code)
    }

    @Test
    fun malformedBlankAndUnknownLinesAreSkipped() {
        val (events, terminal) =
            parse(
                listOf(
                    "not json",
                    "",
                    """{"type":"future","x":1}""",
                    """{"type":"delta"""",
                    """{"type":"delta","text":"ok"}""",
                ).joinToString("\n", postfix = "\n"),
            )
        assertFalse(terminal)
        assertEquals(listOf<ChatEvent>(ChatEvent.Delta("ok")), events)
    }

    @Test
    fun stopsAtFirstTerminalEvent() {
        val (events, terminal) =
            parse(
                """{"type":"error","code":"internal","message":"m"}""" + "\n" + """{"type":"delta","text":"late"}""" +
                    "\n",
            )
        assertTrue(terminal)
        assertEquals(1, events.size)
    }

    @Test
    fun hebrewSurvivesRoundTrip() {
        val (events, _) = parse("{\"type\":\"delta\",\"text\":\"שלום עולם\"}\n")
        assertEquals("שלום עולם", (events.single() as ChatEvent.Delta).text)
    }

    @Test
    fun lastLineWithoutNewlineIsParsed() {
        val (events, _) = parse("{\"type\":\"delta\",\"text\":\"a\"}")
        assertEquals(1, events.size)
    }
}
