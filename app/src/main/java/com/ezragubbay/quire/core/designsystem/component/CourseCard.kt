package com.ezragubbay.quire.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

/** A course in the course list: [title] plus an optional [subtitle] (file count, last activity...). */
@Composable
fun CourseCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val spacing = QuireTheme.spacing
    Card(
        onClick = onClick,
        modifier = modifier.testTag("course-card").fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("course-card-title"))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("course-card-subtitle"),
                )
            }
        }
    }
}
