package com.ezragubbay.quire.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LocalQuireColors = staticCompositionLocalOf { QuireColors.Light }

/**
 * Quire's Material 3 theme. Wrap every screen (and `@Preview`) in it.
 *
 * @param themeMode user appearance choice; [ThemeMode.System] follows the device.
 * @param dynamicColor opt-in Material You colours (Android 12+); ignored below API 31. Off by default; the
 *   Settings toggle (QMOB-E2-S5) passes it in. File-type accents keep the brand palette either way.
 *
 * Also keeps system-bar icon contrast right for edge-to-edge: dark icons over light surfaces and vice versa.
 */
@Composable
fun QuireTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark(systemDark = isSystemInDarkTheme())
    val brand = if (dark) QuireColors.Dark else QuireColors.Light
    val context = LocalContext.current
    val scheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            brand.scheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalQuireColors provides brand.copy(scheme = scheme),
        LocalQuireSpacing provides QuireSpacing(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = QuireTypography, shapes = QuireShapes, content = content)
    }
}

/**
 * Token accessors for the current theme. Colours, type and shapes stay on `MaterialTheme`; Quire-specific
 * tokens (spacing, file-type accents) live here.
 */
object QuireTheme {
    /** The [QuireSpacing] scale. */
    val spacing: QuireSpacing
        @Composable @ReadOnlyComposable
        get() = LocalQuireSpacing.current

    /** Accent colours for [type] in the current theme. */
    @Composable
    @ReadOnlyComposable
    fun fileAccent(type: FileType): FileTypeAccent = LocalQuireColors.current.accent(type)
}
