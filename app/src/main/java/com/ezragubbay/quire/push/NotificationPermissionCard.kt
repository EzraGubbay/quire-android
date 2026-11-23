package com.ezragubbay.quire.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Drives the one-time notification-permission card (ADR 0003 §4.4). */
@HiltViewModel
class NotificationPromptViewModel
    @Inject
    constructor(
        private val store: NotificationPromptStore,
        private val registrar: PushRegistrar,
    ) : ViewModel() {
        val prompt: StateFlow<NotificationPrompt> =
            store.prompt.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                NotificationPrompt(),
            )

        fun onRequesting() {
            viewModelScope.launch { store.markRequested() }
        }

        /** Allow (granted or not) or Not now: hide the card for good and register the resulting categories. */
        fun onAnswered() {
            viewModelScope.launch {
                store.markAnswered()
                registrar.requestSync()
            }
        }
    }

/**
 * Whether a denied `POST_NOTIFICATIONS` request was answered by the system without showing its dialog (the user
 * denied it before, so Android stopped asking). Then the only way left is the app's notification settings.
 *
 * The rationale flag is true only between a first denial and a permanent one, so a dialog that really showed leaves
 * it true either before or after the request. It is false both times either when Android no longer asks, or on a
 * first-ever request the user dismissed without choosing; [requestedBefore] tells those two apart.
 */
fun permissionDialogWasBlocked(
    granted: Boolean,
    requestedBefore: Boolean,
    rationaleBefore: Boolean,
    rationaleAfter: Boolean,
): Boolean = !granted && requestedBefore && !rationaleBefore && !rationaleAfter

/**
 * "Get notified about new course files and homework deadlines" — Allow / Not now. Shown on Courses once, after the
 * first pairing, on Android 13+ while the permission is not granted. Never at launch. Allow asks the system; when the
 * system no longer shows its dialog ([permissionDialogWasBlocked]), it opens the app's notification settings.
 */
@Composable
fun NotificationPermissionCard(viewModel: NotificationPromptViewModel = hiltViewModel()) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    // Already allowed (for example from the Sync settings): nothing to ask, so retire the card silently.
    LaunchedEffect(prompt.pending, granted) { if (prompt.pending && granted) viewModel.onAnswered() }
    // Captured when Allow is tapped, read in the result: survives a configuration change while the dialog is up.
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            val blocked =
                permissionDialogWasBlocked(
                    granted = allowed,
                    requestedBefore = requestedBefore,
                    rationaleBefore = rationaleBefore,
                    rationaleAfter = context.showsNotificationRationale(),
                )
            if (blocked) context.startActivity(appNotificationSettings(context))
            viewModel.onAnswered()
        }
    if (!prompt.pending || granted) return
    PermissionCard(
        onAllow = {
            requestedBefore = prompt.requestedBefore
            rationaleBefore = context.showsNotificationRationale()
            viewModel.onRequesting()
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onNotNow = viewModel::onAnswered,
    )
}

private fun Context.showsNotificationRationale(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true

@Composable
private fun PermissionCard(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
) {
    val spacing = QuireTheme.spacing
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm)
                .testTag("push-permission-card"),
    ) {
        Column(Modifier.padding(spacing.md)) {
            Text(stringResource(R.string.push_permission_title), style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, alignment = Alignment.End),
            ) {
                TextButton(onClick = onNotNow, modifier = Modifier.testTag("action-notifications-not-now")) {
                    Text(stringResource(R.string.push_permission_not_now))
                }
                Button(onClick = onAllow, modifier = Modifier.testTag("action-notifications-allow")) {
                    Text(stringResource(R.string.push_permission_allow))
                }
            }
        }
    }
}

internal fun appNotificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
