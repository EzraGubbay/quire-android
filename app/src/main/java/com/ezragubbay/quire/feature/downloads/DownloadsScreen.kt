package com.ezragubbay.quire.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.component.EmptyState
import com.ezragubbay.quire.core.designsystem.component.LoadingSkeleton
import com.ezragubbay.quire.core.designsystem.component.SectionHeader

/** The Downloads tab: the offline library grouped by course, with storage use, remove / retry and progress. */
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pinning by viewModel.pinning.collectAsStateWithLifecycle()
    DownloadsContent(
        state,
        viewModel::remove,
        viewModel::retry,
        viewModel::removeAll,
        pinning,
        viewModel::pin,
        viewModel::unpin,
    )
}

@Composable
internal fun DownloadsContent(
    state: DownloadsUiState,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemoveAll: () -> Unit,
    pinning: PinUiState = PinUiState(),
    onPin: (String) -> Unit = {},
    onUnpin: (slug: String, removeFiles: Boolean) -> Unit = { _, _ -> },
) {
    Column(Modifier.fillMaxSize().testTag("screen-downloads")) {
        if (pinning.capWarning) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().testTag("storage-cap-warning"),
            ) {
                Text(
                    stringResource(R.string.pin_cap_warning),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (state) {
                DownloadsUiState.Loading -> LoadingSkeleton()
                DownloadsUiState.Empty ->
                    Box(Modifier.fillMaxSize().testTag("downloads-empty")) {
                        EmptyState(
                            title = stringResource(R.string.downloads_empty_title),
                            message = stringResource(R.string.downloads_empty_message),
                        )
                    }
                is DownloadsUiState.Content -> DownloadsList(state, onRemove, onRetry, onRemoveAll, onPin, onUnpin)
            }
        }
    }
}

@Composable
private fun DownloadsList(
    state: DownloadsUiState.Content,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemoveAll: () -> Unit,
    onPin: (String) -> Unit,
    onUnpin: (slug: String, removeFiles: Boolean) -> Unit,
) {
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }
    var unpinning by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.downloads_storage_used, state.storageLabel),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("storage-usage"),
            )
            TextButton(onClick = { confirmRemoveAll = true }, modifier = Modifier.testTag("downloads-remove-all")) {
                Text(stringResource(R.string.downloads_remove_all))
            }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("downloads-list")) {
            state.groups.forEach { group ->
                item(key = "group-${group.courseId}") {
                    Column(Modifier.testTag("download-group-${group.courseId}")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SectionHeader(group.courseName) }
                            FilterChip(
                                selected = group.pinned,
                                onClick = { if (group.pinned) unpinning = group.courseId else onPin(group.courseId) },
                                label = { Text(stringResource(R.string.pin_available_offline)) },
                                modifier = Modifier.padding(end = 16.dp).testTag("download-pin-${group.courseId}"),
                            )
                        }
                    }
                }
                items(group.rows, key = { it.fileId }) { row ->
                    DownloadRow(row, onRemove, onRetry)
                }
            }
        }
    }
    unpinning?.let { slug ->
        UnpinDialog(
            onUnpin = {
                unpinning = null
                onUnpin(slug, it)
            },
            onDismiss = { unpinning = null },
        )
    }
    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            title = { Text(stringResource(R.string.downloads_remove_all_title)) },
            text = { Text(stringResource(R.string.downloads_remove_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveAll = false
                        onRemoveAll()
                    },
                    modifier = Modifier.testTag("downloads-remove-all-confirm"),
                ) { Text(stringResource(R.string.downloads_remove_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAll = false }) { Text(stringResource(R.string.downloads_cancel)) }
            },
        )
    }
}

@Composable
private fun DownloadRow(
    row: DownloadRowUi,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("download-row-${row.fileId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            when (row.status) {
                DownloadRowStatus.Downloading -> {
                    Text(
                        stringResource(R.string.downloads_state_downloading, row.sizeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("download-state-running"),
                    )
                    val progress = row.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            Modifier.fillMaxWidth().testTag("download-progress"),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().testTag("download-progress"))
                    }
                }
                DownloadRowStatus.Done ->
                    Text(
                        stringResource(R.string.downloads_state_done, row.sizeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("download-state-done"),
                    )
                DownloadRowStatus.Failed ->
                    Text(
                        stringResource(R.string.downloads_state_failed, row.error.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("download-state-failed"),
                    )
            }
        }
        if (row.status == DownloadRowStatus.Failed) {
            TextButton(onClick = { onRetry(row.fileId) }, modifier = Modifier.testTag("download-retry")) {
                Text(stringResource(R.string.downloads_retry))
            }
        }
        TextButton(onClick = { onRemove(row.fileId) }, modifier = Modifier.testTag("download-remove")) {
            Text(stringResource(R.string.downloads_remove))
        }
    }
}
