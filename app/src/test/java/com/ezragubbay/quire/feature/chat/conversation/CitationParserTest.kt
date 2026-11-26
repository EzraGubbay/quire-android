package com.ezragubbay.quire.feature.chat.conversation

import com.ezragubbay.quire.core.network.CitationDto
import com.ezragubbay.quire.navigation.ViewerRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CitationParserTest {
    private fun cite(
        n: Int,
        kind: String = "document",
        pageNo: Int? = 7,
    ) = CitationDto(n, kind, "id-$n", "Title $n", pageNo)

    @Test
    fun markers_inOrder_distinct() = assertEquals(listOf(2, 1), CitationParser.markers("a [2] b [1] c [2]"))

    @Test
    fun adjacentMarkers_bothFound() = assertEquals(listOf(1, 2), CitationParser.markers("see [1][2]."))

    @Test
    fun markersInsideFencedCode_ignored() =
        assertEquals(listOf(3), CitationParser.markers("x\n```\nval a = b[1]\n```\ny [3]"))

    @Test
    fun markersInsideInlineCode_ignored() = assertEquals(listOf(2), CitationParser.markers("`a[1]` then [2]"))

    @Test
    fun nonNumericBrackets_ignored() = assertEquals(emptyList<Int>(), CitationParser.markers("[a] [] [x1]"))

    @Test
    fun chips_dropMarkerWithoutMatchingCitation() =
        assertEquals(listOf(cite(1)), CitationParser.chips("one [1] two [9]", listOf(cite(1))))

    @Test
    fun sources_sortedByNumber() =
        assertEquals(listOf(1, 2), CitationParser.sources(listOf(cite(2), cite(1))).map { it.n })

    @Test
    fun documentCitation_mapsToViewerRoute_atPage() =
        assertEquals(ViewerRoute("c", "id-1", 7), cite(1).toViewerRoute("c"))

    @Test
    fun documentCitation_withoutPage_opensAtDefault() =
        assertEquals(ViewerRoute("c", "id-1", null), cite(1, pageNo = null).toViewerRoute("c"))

    @Test
    fun nonDocumentCitation_isNotOpenable() {
        assertNull(cite(1, kind = "note").toViewerRoute("c"))
        assertNull(cite(1, kind = "mystery").toViewerRoute("c"))
    }

    @Test
    fun sourceLabel_includesPageWhenKnown() {
        assertEquals("Title 1, p. 7", cite(1).sourceLabel("p."))
        assertEquals("Title 1", cite(1, pageNo = null).sourceLabel("p."))
    }

    private fun ranges(text: String) = CitationParser.markerMatches(text).map { it.n to it.range }

    @Test
    fun markerMatches_everyOccurrenceWithRanges() = assertEquals(listOf(1 to 2..4, 1 to 8..10), ranges("a [1] b [1]"))

    @Test
    fun markerMatches_adjacentMarkers() = assertEquals(listOf(1 to 0..2, 2 to 3..5), ranges("[1][2]"))

    @Test
    fun markerMatches_streamingPartials_noMatchUntilClosed() {
        listOf("slides [1", "slides [", "[12", "[1 ]").forEach { assertEquals(it, emptyList<Any>(), ranges(it)) }
        assertEquals(listOf(1 to 2..4), ranges("x [1]"))
        assertEquals(listOf(false, false, true), listOf("x [", "x [1", "x [1]").map { ranges(it).isNotEmpty() })
    }

    @Test
    fun markerMatches_edgeCases() {
        assertEquals(listOf(0, 999), listOf("[0]", "[999]").map { ranges(it).single().first })
        listOf("[1000]", "[a]", "[]", "[x1]", "[1a]", "[1, 2]", "[^1]").forEach {
            assertEquals(it, emptyList<Any>(), ranges(it))
        }
        assertEquals(listOf(1 to 1..3), ranges("[[1]]"))
    }

    @Test
    fun isOpenable_onlyForDocuments() {
        assertEquals(true, cite(1).isOpenable)
        listOf("note", "mystery", "").forEach { assertEquals(it, false, cite(1, kind = it).isOpenable) }
    }
}
