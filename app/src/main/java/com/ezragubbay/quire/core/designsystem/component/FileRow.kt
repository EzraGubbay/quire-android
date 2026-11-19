package com.ezragubbay.quire.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

/**
 * One file in a list: [title], a [meta] line (size, date...), a [FileTypeChip], and an optional [trailing] slot
 * for download state. Text wraps rather than truncates, so large fonts and long Hebrew names stay readable.
 */
@Composable
fun FileRow(
    title: String,
    meta: String,
    type: FileType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = QuireTheme.spacing
    Row(
        modifier =
            modifier
                .testTag("file-row")
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("file-row-title"))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FileTypeChip(type)
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false).testTag("file-row-meta"),
                )
            }
        }
        trailing?.invoke()
    }
}
