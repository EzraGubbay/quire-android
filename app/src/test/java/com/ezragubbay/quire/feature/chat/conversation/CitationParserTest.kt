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
}
