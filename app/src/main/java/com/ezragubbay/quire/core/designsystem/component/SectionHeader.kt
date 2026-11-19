package com.ezragubbay.quire.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

/** A list section title; announced as a heading by TalkBack. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .testTag("section-header")
                .fillMaxWidth()
                .padding(horizontal = QuireTheme.spacing.md, vertical = QuireTheme.spacing.sm)
                .semantics { heading() },
    )
}
