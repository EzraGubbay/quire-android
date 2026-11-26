package com.ezragubbay.quire.feature.chat.conversation

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode
import com.ezragubbay.quire.core.network.CitationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationCitationsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val doc = CitationDto(1, "document", "d1", "Lecture 3", 4)
    private val note = CitationDto(2, "note", "n2", "My note", null)
    private val opened = mutableListOf<CitationDto>()

    private fun show(
        state: ConversationUiState,
        mode: ThemeMode = ThemeMode.Light,
    ) {
        rule.setContent {
            QuireTheme(themeMode = mode) {
                ConversationContent(state, {}, {}, {}, {}, {}, onOpenCitation = { opened += it })
            }
        }
        rule.waitForIdle()
    }

    private fun textViews(v: View): List<TextView> =
        (if (v is TextView) listOf(v) else emptyList()) +
            ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { textViews(g.getChildAt(it)) } }.orEmpty())

    private fun chipSpans(): List<CitationChipSpan> =
        textViews(rule.activity.window.decorView).flatMap { tv ->
            (tv.text as? android.text.Spanned)?.getSpans(0, tv.text.length, CitationChipSpan::class.java)?.toList()
                ?: emptyList()
        }

    private val settled =
        ConversationUiState(
            history =
                listOf(
                    Bubble(Role.Assistant, "Flipping [1] and sliding [2].", citations = listOf(doc, note)),
                ),
            historyLoaded = true,
        )

    @Test
    fun settledAnswer_hasInlineChipWithTag_andClickOpensDoc() {
        show(settled)
        val chips = chipSpans()
        assertEquals(2, chips.size)
        assertEquals("citation-chip-1", chips.first { it.n == 1 }.testTag)
        val tv =
            textViews(rule.activity.window.decorView).first {
                val t = it.text as? android.text.Spanned
                t != null && t.getSpans(0, t.length, CitationClickSpan::class.java).isNotEmpty()
            }
        val sp = tv.text as android.text.Spanned
        sp.getSpans(0, sp.length, CitationClickSpan::class.java).single().onClick(tv)
        assertEquals(listOf(doc), opened)
        assertEquals(0, rule.onAllNodesWithTag("citation-chip-1").fetchSemanticsNodes().size)
    }

    @Test
    fun streamingPartialMarker_noChips() {
        show(ConversationUiState(live = listOf(Bubble(Role.Assistant, "x [1")), streaming = true, historyLoaded = true))
        assertTrue(chipSpans().isEmpty())
    }

    @Test
    fun streamingClosedMarkerWithCitations_noChipsUntilSettled() {
        val live = listOf(Bubble(Role.Assistant, "x [1]", citations = listOf(doc)))
        show(ConversationUiState(live = live, streaming = true, historyLoaded = true))
        assertTrue(chipSpans().isEmpty())
    }

    @Test
    fun sourcesRows_documentClickable_noteDisabled() {
        show(settled)
        rule.onNodeWithTag("source-row-1").assertIsEnabled().performClick()
        assertEquals(listOf(doc), opened)
        rule.onNodeWithTag("source-row-2").assertIsNotEnabled()
    }

    @Test
    fun darkTheme_chipUsesDarkSecondaryContainer() {
        show(settled, ThemeMode.Dark)
        assertEquals(0xFF4D2A2F.toInt(), chipSpans().first().style.container)
    }
}
