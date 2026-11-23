package com.ezragubbay.quire.feature.notes

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app's Markwon configuration only typesets `$$…$$`: an inline `$…$` (what the server's notes and chat answers
 * use) comes out as raw text unless it goes through [mathMarkdownBlocks] first.
 */
@RunWith(RobolectricTestRunner::class)
class MarkwonMathRenderTest {
    private val markwon = buildMarkwon(ApplicationProvider.getApplicationContext(), Color.BLACK, TEXT_PX)

    private fun mathSpans(markdown: String): Int {
        val spanned = markwon.toMarkdown(markdown)
        return spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java).size
    }

    private fun renderedMathSpans(markdown: String): Int =
        mathMarkdownBlocks(markdown).sumOf { block ->
            when (block) {
                is NoteBlock.Text -> mathSpans(block.markdown)
                is NoteBlock.Math -> mathSpans("$$\n${block.latex}\n$$")
            }
        }

    @Test
    fun rawSingleDollarInline_isNotTypesetByMarkwon() {
        assertEquals(0, mathSpans("Inline \$e^{i\\pi} + 1 = 0\$ here"))
    }

    @Test
    fun singleDollarInline_isTypesetAfterNormalising() {
        assertEquals(1, renderedMathSpans("Inline \$e^{i\\pi} + 1 = 0\$ here"))
    }

    @Test
    fun latexStyleDelimiters_areTypesetAfterNormalising() {
        assertEquals(2, renderedMathSpans("Let \\(f(x) = x^2\\). Then\n\\[\n\\int_0^1 f = \\tfrac{1}{3}\n\\]"))
    }

    @Test
    fun midSentenceDisplayMath_isTypeset() {
        assertEquals(1, renderedMathSpans("Convolution is defined as \$\$ (f * g)(t) \$\$\n\n- It is commutative"))
    }

    @Test
    fun currencyAndCode_stayText() {
        assertEquals(0, renderedMathSpans("It costs \$5 and \$10; run `echo \$x\$`."))
    }

    private companion object {
        const val TEXT_PX = 42f
    }
}
