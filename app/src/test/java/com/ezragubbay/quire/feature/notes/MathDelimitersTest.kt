package com.ezragubbay.quire.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class MathDelimitersTest {
    // --- normalizeMathDelimiters: parity with the web app's normalizeMathDelimiters ----------------------------------

    @Test
    fun backslashParens_becomeSingleDollar() {
        assertEquals("Euler: \$e^{i\\pi}\$.", normalizeMathDelimiters("Euler: \\(e^{i\\pi}\\)."))
    }

    @Test
    fun backslashParensWithInnerSpaces_areTrimmed() {
        assertEquals("so \$x+1\$ holds", normalizeMathDelimiters("so \\( x+1 \\) holds"))
    }

    @Test
    fun backslashBrackets_becomeDisplayMathOnTheirOwnLines() {
        assertEquals("Define\n\n\$\$\nx^2\n\$\$\n\nthen", normalizeMathDelimiters("Define\n\\[x^2\\]\nthen"))
    }

    @Test
    fun multiLineBackslashBrackets_becomeDollarFences() {
        assertEquals("\$\$\n\\int f\n\$\$", normalizeMathDelimiters("\\[\n\\int f\n\\]"))
    }

    @Test
    fun midParagraphDisplayMath_isLiftedOntoItsOwnLines() {
        // The server's chat-stream-markdown contract fixture writes the block equation mid-sentence like this.
        assertEquals(
            "Convolution is defined as \n\n\$\$\n(f * g)(t)\n\$\$\n\nnext",
            normalizeMathDelimiters("Convolution is defined as \$\$ (f * g)(t) \$\$\n\nnext"),
        )
    }

    @Test
    fun displayMathOnListAndQuoteLines_isLeftInline() {
        val md = "- item \$\$x\$\$\n> quote \$\$y\$\$\n1. step \$\$z\$\$"
        assertEquals(md, normalizeMathDelimiters(md))
    }

    @Test
    fun delimitersInsideCode_areUntouched() {
        val md = "Run `\\(x\\)` and `\$\$a\$\$`:\n```\nprint(\"\\[y\\]\")\n\$\$ z \$\$\n```"
        assertEquals(md, normalizeMathDelimiters(md))
    }

    @Test
    fun unclosedFence_isCodeToTheEnd() {
        val md = "```\n\\(x\\)"
        assertEquals(md, normalizeMathDelimiters(md))
    }

    // --- markwonInlineMath: `$…$` to Markwon's inline `$$…$$` --------------------------------------------------------

    @Test
    fun singleDollarInline_becomesMarkwonInline() {
        assertEquals(
            "Inline \$\$e^{i\\pi} + 1 = 0\$\$ renders.",
            markwonInlineMath("Inline \$e^{i\\pi} + 1 = 0\$ renders."),
        )
    }

    @Test
    fun severalInlineFormulas_areEachRewritten() {
        assertEquals("\$\$a\$\$ and \$\$b_1\$\$", markwonInlineMath("\$a\$ and \$b_1\$"))
    }

    @Test
    fun existingDoubleDollar_isKept() {
        val md = "inline \$\$x\$\$ stays"
        assertEquals(md, markwonInlineMath(md))
    }

    @Test
    fun currency_isNotMath() {
        val md = "It costs \$5 and \$10 in total."
        assertEquals(md, markwonInlineMath(md))
    }

    @Test
    fun currencyThenMath_rewritesOnlyTheMath() {
        assertEquals(
            "Between \$5 and \$10, take \$\$x\$\$.",
            markwonInlineMath("Between \$5 and \$10, take \$x\$."),
        )
    }

    @Test
    fun closingDollarFollowedByDigit_isNotMath() {
        val md = "from \$3 to 4\$5"
        assertEquals(md, markwonInlineMath(md))
    }

    @Test
    fun escapedDollar_isKept() {
        val md = "a literal \\\$x\\\$ here"
        assertEquals(md, markwonInlineMath(md))
    }

    @Test
    fun inlineMath_doesNotSpanParagraphs() {
        val md = "price \$x\n\nnew paragraph\$"
        assertEquals(md, markwonInlineMath(md))
    }

    @Test
    fun inlineMathAcrossASoftLineBreak_isRewritten() {
        assertEquals("\$\$a +\nb\$\$", markwonInlineMath("\$a +\nb\$"))
    }

    @Test
    fun dollarsInsideCode_areUntouched() {
        val md = "Use `\$x\$` or\n```bash\necho \$HOME \$PATH\$\n```\nthen \$y\$"
        assertEquals("Use `\$x\$` or\n```bash\necho \$HOME \$PATH\$\n```\nthen \$\$y\$\$", markwonInlineMath(md))
    }

    @Test
    fun unterminatedInlineMathMidStream_isLeftAsIs() {
        val md = "so the value \$x^"
        assertEquals(md, markwonInlineMath(md))
    }

    // --- mathMarkdownBlocks: the render pipeline used by the notes reader and chat answers ---------------------------

    @Test
    fun readerFixture_rendersBlockAndInlineMath() {
        val md =
            "# Convolution\n\nin Hebrew קונבולוציה:\n\n\$\$ (f * g)(t) \$\$\n\n" +
                "Inline math like \$e^{i\\pi} + 1 = 0\$ renders in the paragraph. See [[lecture-2-notes]].\n"
        assertEquals(
            listOf(
                NoteBlock.Text("# Convolution\n\nin Hebrew קונבולוציה:"),
                NoteBlock.Math("(f * g)(t)"),
                NoteBlock.Text(
                    "Inline math like \$\$e^{i\\pi} + 1 = 0\$\$ renders in the paragraph. See [[lecture-2-notes]].",
                ),
            ),
            mathMarkdownBlocks(md),
        )
    }

    @Test
    fun chatContractAnswer_surfacesTheBlockEquation() {
        val md =
            "## Convolution\n\nConvolution is defined as \$\$ (f * g)(t) = \\int f(\\tau)\\, d\\tau \$\$\n\n" +
                "- It is commutative\n- See `conv()` [1]."
        val blocks = mathMarkdownBlocks(md)
        assertEquals(NoteBlock.Math("(f * g)(t) = \\int f(\\tau)\\, d\\tau"), blocks[1])
        assertEquals(NoteBlock.Text("- It is commutative\n- See `conv()` [1]."), blocks[2])
    }

    @Test
    fun latexStyleAnswer_rendersInlineAndDisplay() {
        val md = "Let \\(f(x) = x^2\\). Then\n\\[\n\\int_0^1 f = \\tfrac{1}{3}\n\\]"
        assertEquals(
            listOf(NoteBlock.Text("Let \$\$f(x) = x^2\$\$. Then"), NoteBlock.Math("\\int_0^1 f = \\tfrac{1}{3}")),
            mathMarkdownBlocks(md),
        )
    }
}
