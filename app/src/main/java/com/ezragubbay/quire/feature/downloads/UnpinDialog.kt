package com.ezragubbay.quire.feature.downloads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.ezragubbay.quire.R

/** Unpinning asks whether the course's downloaded files stay on the phone or go. */
@Composable
fun UnpinDialog(
    onUnpin: (removeFiles: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("unpin-dialog"),
        title = { Text(stringResource(R.string.pin_unpin_title)) },
        text = { Text(stringResource(R.string.pin_unpin_message)) },
        confirmButton = {
            TextButton(onClick = { onUnpin(true) }, modifier = Modifier.testTag("unpin-remove")) {
                Text(stringResource(R.string.pin_unpin_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = { onUnpin(false) }, modifier = Modifier.testTag("unpin-keep")) {
                Text(stringResource(R.string.pin_unpin_keep))
            }
        },
    )
}
