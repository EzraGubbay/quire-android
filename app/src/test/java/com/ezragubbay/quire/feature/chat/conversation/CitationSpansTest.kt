package com.ezragubbay.quire.feature.chat.conversation

import android.graphics.Color
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.style.TtsSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.core.network.CitationDto
import com.ezragubbay.quire.feature.notes.NoteBlock
import com.ezragubbay.quire.feature.notes.buildMarkwon
import com.ezragubbay.quire.feature.notes.mathMarkdownBlocks
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CitationSpansTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val markwon = buildMarkwon(context, Color.BLACK, 42f)
    private val doc = CitationDto(1, "document", "d1", "Lecture 3", 4)
    private val note = CitationDto(2, "note", "n2", "My note", null)
    private val style = CitationChipStyle(1, 2, 3)
    private val opened = mutableListOf<CitationDto>()

    private fun decorate(
        markdown: String,
        byN: Map<Int, CitationDto> = mapOf(1 to doc, 2 to note),
    ): android.text.Spannable =
        (markwon.toMarkdown(markdown) as android.text.Spannable).applyCitationChips(byN, style, { "label ${it.n}" }) {
            opened += it
        }

    private fun chips(s: android.text.Spanned) = s.getSpans(0, s.length, CitationChipSpan::class.java).toList()

    @Test
    fun docAndNote_chips_oneClickable_twoTts() {
        val s = decorate("see [1] and [2].")
        assertEquals(2, chips(s).size)
        val clicks = s.getSpans(0, s.length, CitationClickSpan::class.java)
        assertEquals(1, clicks.size)
        assertEquals(s.toString().indexOf("[1]"), s.getSpanStart(clicks[0]))
        assertEquals(2, s.getSpans(0, s.length, TtsSpan::class.java).size)
    }

    @Test
    fun unknownMarker_noSpans() = assertEquals(0, chips(decorate("see [9]")).size)

    @Test
    fun repeats_onlyFirstCarriesTag() {
        val s = decorate("[1] then [1]")
        val c = chips(s).sortedBy { s.getSpanStart(it) }
        assertEquals(listOf<String?>("citation-chip-1", null), c.map { it.testTag })
    }

    @Test
    fun inlineCode_notDecorated() {
        val s = decorate("`b[1]` then [1]")
        val c = chips(s)
        assertEquals(1, c.size)
        assertEquals(s.toString().lastIndexOf("[1]"), s.getSpanStart(c[0]))
    }

    @Test
    fun fencedCode_notDecorated() = assertEquals(0, chips(decorate("```\nb[1]\n```")).size)

    @Test
    fun inlineMath_noChipOverlapsMath() {
        val blocks = mathMarkdownBlocks("see \$x[1]\$ and [1]").filterIsInstance<NoteBlock.Text>()
        val s = decorate(blocks.joinToString("") { it.markdown })
        val math = s.getSpans(0, s.length, AsyncDrawableSpan::class.java)
        assertTrue("expected inline math span", math.isNotEmpty())
        val c = chips(s)
        assertEquals(1, c.size)
        math.forEach { m ->
            assertTrue(s.getSpanEnd(c[0]) <= s.getSpanStart(m) || s.getSpanStart(c[0]) >= s.getSpanEnd(m))
        }
    }

    @Test
    fun markdownLink_notDecorated() = assertEquals(0, chips(decorate("[[1]](https://x)")).size)

    @Test
    fun click_opensCitation() {
        val s = decorate("[1]")
        s.getSpans(0, s.length, CitationClickSpan::class.java).single().onClick(View(context))
        assertEquals(listOf(doc), opened)
    }

    @Test
    fun emptyMap_returnsSameInstanceUntouched() {
        val sb = SpannableStringBuilder("a [1]")
        assertSame(sb, sb.applyCitationChips(emptyMap(), style, { "" }) {})
        assertEquals(0, sb.getSpans(0, sb.length, Any::class.java).size)
    }

    @Test
    fun chipSize_leavesFontMetricsUnchanged() {
        val fm =
            Paint.FontMetricsInt().apply {
                ascent = -30
                descent = 10
                top = -35
                bottom = 12
            }
        val span = CitationChipSpan(1, doc, style, null)
        val width = span.getSize(Paint().apply { textSize = 40f }, "[1]", 0, 3, fm)
        assertEquals(listOf(-30, 10, -35, 12), listOf(fm.ascent, fm.descent, fm.top, fm.bottom))
        assert(width > 0)
        assertNull(span.testTag)
    }
}
