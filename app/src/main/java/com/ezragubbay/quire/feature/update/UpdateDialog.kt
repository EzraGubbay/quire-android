package com.ezragubbay.quire.feature.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.R

/** A newer Quire is out: open Obtainium (or the release page when it is not installed), or snooze this version. */
@Composable
fun UpdateDialog(
    version: String,
    obtainiumInstalled: Boolean,
    onOpen: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("update-dialog"),
        title = { Text(stringResource(R.string.update_available_title, version)) },
        text = { Text(stringResource(R.string.update_available_message)) },
        confirmButton = {
            TextButton(onClick = onOpen, modifier = Modifier.testTag("update-open")) {
                Text(
                    stringResource(
                        if (obtainiumInstalled) R.string.update_open_obtainium else R.string.update_view_release,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, modifier = Modifier.testTag("update-later")) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}

/** Shows the update prompt over the app, but only while [visible] (signed in: never over Pair or Scan). */
@Composable
fun UpdatePromptHost(
    viewModel: UpdateViewModel,
    visible: Boolean,
) {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val shown = prompt
    if (visible && shown != null) {
        val context = LocalContext.current
        val installed = remember(shown) { ObtainiumLauncher.isInstalled(context.packageManager) }
        UpdateDialog(
            version = shown.version,
            obtainiumInstalled = installed,
            onOpen = {
                ObtainiumLauncher.open(context, shown.releaseUrl)
                viewModel.onOpened(shown)
            },
            onLater = { viewModel.onLater(shown) },
            onDismiss = { viewModel.onDismissed(shown) },
        )
    }
}
