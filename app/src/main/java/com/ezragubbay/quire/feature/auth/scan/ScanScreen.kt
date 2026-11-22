package com.ezragubbay.quire.feature.auth.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ezragubbay.quire.BuildConfig
import com.ezragubbay.quire.R
import java.util.concurrent.Executors

const val SCREEN_SCAN_TAG = "screen-scan"
const val SCAN_ERROR_TAG = "scan-error"
const val SCAN_PERMISSION_DENIED_TAG = "scan-permission-denied"

private enum class CameraAccess { Unknown, Rationale, Granted, Denied }

/** QR scanner: asks for the camera in context, then reports a valid `quire://pair` payload via [onScanned]. */
@Composable
fun ScanScreen(
    onScanned: (serverUrl: String, code: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var access by remember {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (granted) CameraAccess.Granted else CameraAccess.Rationale)
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            access = if (ok) CameraAccess.Granted else CameraAccess.Denied
        }
    Surface(modifier = Modifier.fillMaxSize().testTag(SCREEN_SCAN_TAG)) {
        when (access) {
            CameraAccess.Granted -> CameraPreview(onScanned = onScanned, onBack = onBack)
            CameraAccess.Unknown, CameraAccess.Rationale ->
                Message(
                    text = stringResource(R.string.scan_rationale),
                    primaryLabel = stringResource(R.string.scan_grant),
                    onPrimary = { launcher.launch(Manifest.permission.CAMERA) },
                    onBack = onBack,
                )
            CameraAccess.Denied ->
                Message(
                    text = stringResource(R.string.scan_denied),
                    primaryLabel = stringResource(R.string.scan_open_settings),
                    onPrimary = {
                        val uri = Uri.fromParts("package", context.packageName, null)
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                    },
                    onBack = onBack,
                    modifier = Modifier.testTag(SCAN_PERMISSION_DENIED_TAG),
                )
        }
    }
}

@Composable
private fun Message(
    text: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineMedium)
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primaryLabel) }
        BackButton(onBack)
    }
}

@Composable
private fun CameraPreview(
    onScanned: (serverUrl: String, code: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notQuire by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            provider = cameraProvider
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis =
                ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                    it.setAnalyzer(
                        executor,
                        QrAnalyzer { text ->
                            when (val result = PairUriParser.parse(text, BuildConfig.ALLOW_CLEARTEXT_LOCAL)) {
                                is PairUriResult.Valid ->
                                    if (!done) {
                                        done = true
                                        previewView.post { onScanned(result.serverUrl, result.code) }
                                    }
                                is PairUriResult.Invalid -> notQuire = true
                            }
                        },
                    )
                }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(if (notQuire) R.string.scan_not_quire else R.string.scan_hint),
                style = MaterialTheme.typography.titleMedium,
                color = if (notQuire) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(SCAN_ERROR_TAG),
            )
            BackButton(onBack)
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_back)) }
}
