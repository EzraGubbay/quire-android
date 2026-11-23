package com.ezragubbay.quire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.feature.auth.PairViewModel
import com.ezragubbay.quire.feature.settings.SettingsViewModel
import com.ezragubbay.quire.navigation.AppViewModel
import com.ezragubbay.quire.navigation.QuireApp
import com.ezragubbay.quire.push.PushSession
import com.ezragubbay.quire.sync.SyncCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

const val APP_ROOT_TAG = "app-root"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var syncCoordinator: SyncCoordinator

    @Inject lateinit var pushSession: PushSession

    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val appViewModel: AppViewModel by viewModels()
    private val pairViewModel: PairViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until the persisted theme and session are read so the first frame is already themed.
        splash.setKeepOnScreenCondition { !settingsViewModel.loaded.value || appViewModel.signedIn.value == null }
        // Schedule background metadata sync (idempotent) and queue the app-start sync if the last one is stale.
        syncCoordinator.start()
        // Register for push on every app start (idempotent per process; a no-op when signed out or not configured).
        pushSession.onAppStart()
        enableEdgeToEdge()
        // A recreated activity keeps its ViewModel, so re-check the stored session on every (re)launch.
        appViewModel.validateSession()
        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            QuireTheme(themeMode = settings.themeMode, dynamicColor = settings.useDeviceColors) {
                QuireApp(appViewModel, pairViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Notifications may have been allowed or turned off in system settings while away: re-register if so.
        pushSession.onResume()
    }
}
