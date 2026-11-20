package com.ezragubbay.quire.feature.chat.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.network.CitationDto

/**
 * The citations under an answer: tappable `[n]` chips for markers the text uses, then a Sources row listing every
 * cited item (title, page). Only document citations open ([onOpen]); others are shown but disabled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnswerCitations(
    text: String,
    citations: List<CitationDto>,
    onOpen: (CitationDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (citations.isEmpty()) return
    val chips = CitationParser.chips(text, citations)
    val sources = CitationParser.sources(citations)
    val pageLabel = stringResource(R.string.conversation_source_page)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { c ->
                    AssistChip(
                        onClick = { onOpen(c) },
                        enabled = c.toViewerRoute("") != null,
                        label = { Text("[${c.n}]") },
                        modifier = Modifier.testTag("citation-chip-${c.n}"),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().testTag("sources-row")) {
            Text(stringResource(R.string.conversation_sources), style = MaterialTheme.typography.labelMedium)
            sources.forEach { c ->
                val openable = c.toViewerRoute("") != null
                Text(
                    text =
                        "[${c.n}] ${c.sourceLabel(pageLabel)}" +
                            if (openable) "" else " — ${stringResource(R.string.conversation_source_unavailable)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp).testTag("source-row-${c.n}"),
                )
            }
        }
    }
}
