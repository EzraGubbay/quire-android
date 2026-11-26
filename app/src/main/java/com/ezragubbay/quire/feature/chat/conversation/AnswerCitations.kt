package com.ezragubbay.quire.feature.chat.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.network.CitationDto

/**
 * The Sources list under an answer: every cited item (title, page), ordered by number. The inline `[n]` chips live in
 * the answer text itself ([applyCitationChips]); here only document citations open ([onOpen]), others are disabled.
 */
@Composable
fun AnswerCitations(
    citations: List<CitationDto>,
    onOpen: (CitationDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (citations.isEmpty()) return
    val sources = remember(citations) { CitationParser.sources(citations) }
    val pageLabel = stringResource(R.string.conversation_source_page)
    val unavailable = stringResource(R.string.conversation_source_unavailable)
    val openLabel = stringResource(R.string.conversation_source_open)
    Column(modifier.fillMaxWidth().testTag("sources-row"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.conversation_sources), style = MaterialTheme.typography.labelMedium)
        sources.forEach { c ->
            Text(
                text = "[${c.n}] ${c.sourceLabel(pageLabel)}" + if (c.isOpenable) "" else " — $unavailable",
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("source-row-${c.n}")
                        .clickable(enabled = c.isOpenable, role = Role.Button, onClickLabel = openLabel) { onOpen(c) }
                        .minimumInteractiveComponentSize()
                        .padding(vertical = 2.dp),
            )
        }
    }
}
