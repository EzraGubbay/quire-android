package com.ezragubbay.quire.feature.chat.conversation

import com.ezragubbay.quire.core.network.CitationDto
import com.ezragubbay.quire.navigation.ViewerRoute

/** Pure `[n]` marker parsing and citation mapping for assistant answers. */
object CitationParser {
    private val fenced = Regex("```.*?(```|\\z)", RegexOption.DOT_MATCHES_ALL)
    private val inline = Regex("`[^`\\n]*`")
    private val marker = Regex("\\[(\\d{1,3})]")

    /** Every `[n]` occurrence in [text] in order, repeats included; code is excluded later by span overlap. */
    fun markerMatches(text: CharSequence): List<MarkerMatch> =
        marker.findAll(text).map { MarkerMatch(it.groupValues[1].toInt(), it.range) }.toList()

    /** Distinct `[n]` markers in order of first appearance, ignoring any inside code (fenced or inline). */
    fun markers(text: String): List<Int> {
        val prose = text.replace(fenced, " ").replace(inline, " ")
        return marker
            .findAll(prose)
            .map { it.groupValues[1].toInt() }
            .distinct()
            .toList()
    }

    /** The citations referenced by a marker in [text], in marker order; a marker with no citation is dropped. */
    fun chips(
        text: String,
        citations: List<CitationDto>,
    ): List<CitationDto> {
        val byN = citations.associateBy { it.n }
        return markers(text).mapNotNull { byN[it] }
    }

    /** Every cited item, ordered by its number, for the Sources row. */
    fun sources(citations: List<CitationDto>): List<CitationDto> = citations.sortedBy { it.n }
}

/** One `[n]` marker occurrence: its number and the index range of the whole `[n]` in the text. */
data class MarkerMatch(
    val n: Int,
    val range: IntRange,
)

private const val KIND_DOCUMENT = "document"

/** Whether this citation can be opened (only documents open in the PDF viewer). */
val CitationDto.isOpenable: Boolean get() = kind == KIND_DOCUMENT

/** Only a document citation opens (in the PDF viewer at its page); notes and unknown kinds are disabled. */
fun CitationDto.toViewerRoute(courseSlug: String): ViewerRoute? =
    if (isOpenable) ViewerRoute(courseSlug, id, pageNo) else null

/** "Title, p. 7" ([pageLabel] is the localized page abbreviation), or just the title when the page is unknown. */
fun CitationDto.sourceLabel(pageLabel: String): String = if (pageNo == null) title else "$title, $pageLabel $pageNo"
