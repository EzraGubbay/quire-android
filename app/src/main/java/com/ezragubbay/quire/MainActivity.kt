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
import dagger.hilt.android.AndroidEntryPoint

const val APP_ROOT_TAG = "app-root"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val appViewModel: AppViewModel by viewModels()
    private val pairViewModel: PairViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until the persisted theme and session are read so the first frame is already themed.
        splash.setKeepOnScreenCondition { !settingsViewModel.loaded.value || appViewModel.signedIn.value == null }
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
}
