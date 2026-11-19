package com.ezragubbay.quire.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {
    @Test
    fun emptyMarkdown_hasNoBlocks() {
        assertTrue(segmentMarkdown("").isEmpty())
        assertTrue(segmentMarkdown("\n\n  \n").isEmpty())
    }

    @Test
    fun plainProse_isOneTextBlock() {
        assertEquals(listOf(NoteBlock.Text("# Title\n\nHello")), segmentMarkdown("# Title\n\nHello\n"))
    }

    @Test
    fun singleLineBlockMath_splitsProse() {
        val md = "Before\n\n$$ x^2 $$\n\nAfter"
        assertEquals(
            listOf(NoteBlock.Text("Before"), NoteBlock.Math("x^2"), NoteBlock.Text("After")),
            segmentMarkdown(md),
        )
    }

    @Test
    fun multiLineBlockMath_isCollected() {
        val md = "$$\n\\int_0^1 f\n= 1\n$$\ntail"
        assertEquals(
            listOf(NoteBlock.Math("\\int_0^1 f\n= 1"), NoteBlock.Text("tail")),
            segmentMarkdown(md),
        )
    }

    @Test
    fun inlineMath_staysInProse() {
        val md = "Inline \$e^{i\\pi}+1=0\$ here"
        assertEquals(listOf(NoteBlock.Text(md)), segmentMarkdown(md))
    }

    @Test
    fun mathInsideCodeFence_isNotMath() {
        val md = "```\n$$ x $$\n```"
        assertEquals(listOf(NoteBlock.Text(md)), segmentMarkdown(md))
    }

    @Test
    fun unterminatedBlockMath_fallsBackToText() {
        val md = "a\n$$\nx"
        assertEquals(listOf(NoteBlock.Text(md)), segmentMarkdown(md))
    }

    @Test
    fun hebrewFixture_segmentsIntoThreeBlocks() {
        val md =
            "# Convolution\n\nin Hebrew קונבולוציה:\n\n$$ (f * g)(t) $$\n\nInline \$a\$ and [[lecture-2-notes]].\n"
        val blocks = segmentMarkdown(md)
        assertEquals(3, blocks.size)
        assertEquals(NoteBlock.Math("(f * g)(t)"), blocks[1])
    }
}
