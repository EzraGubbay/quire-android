package com.ezragubbay.quire.feature.upload

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.core.upload.UploadItem
import com.ezragubbay.quire.core.upload.UploadTab

private val MAX_SECTION_HEIGHT = 240.dp

/**
 * Wraps a course tab with its upload entry point: the pending/failed uploads above the [content], and an Upload
 * button overlaid bottom-end (`action-upload-file` on Files, `action-upload-note` on Notes).
 */
@Composable
fun UploadTabFrame(
    tab: UploadTab,
    uploads: List<UploadItem>,
    onPicked: (List<Uri>) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDismiss: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val pick = rememberUploadPicker(tab, onPicked)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (uploads.isNotEmpty()) UploadSection(uploads, onRetry, onCancel, onDismiss)
            Box(Modifier.weight(1f)) { content() }
        }
        ExtendedFloatingActionButton(
            onClick = pick,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.upload_button)) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(QuireTheme.spacing.md)
                    .testTag(if (tab == UploadTab.FILES) "action-upload-file" else "action-upload-note"),
        )
    }
}

/** "Uploads": one row per pending or failed upload. */
@Composable
fun UploadSection(
    items: List<UploadItem>,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val spacing = QuireTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = MAX_SECTION_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .testTag("upload-section"),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            stringResource(R.string.upload_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        items.forEach { UploadRow(it, onRetry, onCancel, onDismiss) }
    }
}

@Composable
private fun UploadRow(
    item: UploadItem,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val spacing = QuireTheme.spacing
    val failed = item.state == UploadItem.State.FAILED
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("upload-row-${item.id}"),
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(item.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                stateLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("upload-state-${stateTag(item.state)}"),
            )
            if (item.state == UploadItem.State.UPLOADING && item.sizeBytes > 0) {
                LinearProgressIndicator(
                    progress = { (item.progressBytes.toFloat() / item.sizeBytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (failed && !item.error.isNullOrBlank()) {
                Text(item.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (failed) {
                    TextButton(onClick = { onRetry(item.id) }, modifier = Modifier.testTag("action-retry-upload")) {
                        Text(stringResource(R.string.upload_retry))
                    }
                    TextButton(onClick = { onDismiss(item.id) }, modifier = Modifier.testTag("action-dismiss-upload")) {
                        Text(stringResource(R.string.upload_dismiss))
                    }
                } else {
                    TextButton(onClick = { onCancel(item.id) }, modifier = Modifier.testTag("action-cancel-upload")) {
                        Text(stringResource(R.string.upload_cancel))
                    }
                }
            }
        }
    }
}

private fun stateTag(state: UploadItem.State) =
    when (state) {
        UploadItem.State.QUEUED -> "queued"
        UploadItem.State.UPLOADING -> "running"
        UploadItem.State.PROCESSING -> "processing"
        UploadItem.State.FAILED -> "failed"
    }

@Composable
private fun stateLabel(item: UploadItem): String =
    when (item.state) {
        UploadItem.State.QUEUED -> stringResource(R.string.upload_state_queued)
        UploadItem.State.UPLOADING -> {
            val pct =
                if (item.sizeBytes >
                    0
                ) {
                    (item.progressBytes * PERCENT / item.sizeBytes).toInt().coerceIn(0, PERCENT)
                } else {
                    0
                }
            stringResource(R.string.upload_state_uploading, pct)
        }
        UploadItem.State.PROCESSING -> stringResource(R.string.upload_state_processing)
        UploadItem.State.FAILED -> stringResource(R.string.upload_state_failed)
    }

private const val PERCENT = 100
