package com.ezragubbay.quire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.navigation.AppShell

const val APP_ROOT_TAG = "app-root"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuireTheme {
                AppShell()
            }
        }
    }
}
