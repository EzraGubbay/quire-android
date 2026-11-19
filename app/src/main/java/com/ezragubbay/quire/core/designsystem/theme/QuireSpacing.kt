package com.ezragubbay.quire.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp-grid spacing scale (`xxs` is the 2dp hairline exception). Read it with `QuireTheme.spacing`. */
@Immutable
data class QuireSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
)

val LocalQuireSpacing = staticCompositionLocalOf { QuireSpacing() }
