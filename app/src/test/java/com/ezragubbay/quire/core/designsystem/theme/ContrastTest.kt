package com.ezragubbay.quire.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.x contrast helpers (pure JVM). */
internal fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

internal fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

class ContrastTest {
    private val schemes: Map<String, ColorScheme> =
        mapOf("light" to QuireColors.Light.scheme, "dark" to QuireColors.Dark.scheme)

    /** Body-text pairs: foreground on background, WCAG AA 4.5:1. */
    private fun textPairs(s: ColorScheme): Map<String, Pair<Color, Color>> =
        mapOf(
            "onPrimary/primary" to (s.onPrimary to s.primary),
            "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondary/secondary" to (s.onSecondary to s.secondary),
            "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
            "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
            "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
            "onError/error" to (s.onError to s.error),
            "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
            "onBackground/background" to (s.onBackground to s.background),
            "onSurface/surface" to (s.onSurface to s.surface),
            "onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
            "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
            "inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
            "primary/surface (links)" to (s.primary to s.surface),
            "onSurface/surfaceContainerLowest" to (s.onSurface to s.surfaceContainerLowest),
            "onSurface/surfaceContainerLow" to (s.onSurface to s.surfaceContainerLow),
            "onSurface/surfaceContainer" to (s.onSurface to s.surfaceContainer),
            "onSurface/surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
            "onSurface/surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
            "onSurfaceVariant/surfaceContainerHighest" to (s.onSurfaceVariant to s.surfaceContainerHighest),
        )

    /** Large text and non-text (icons, outlines): 3:1. */
    private fun graphicPairs(s: ColorScheme): Map<String, Pair<Color, Color>> =
        mapOf(
            "outline/background" to (s.outline to s.background),
            "outline/surfaceContainerHighest" to (s.outline to s.surfaceContainerHighest),
            "inversePrimary/inverseSurface" to (s.inversePrimary to s.inverseSurface),
            "error/surface" to (s.error to s.surface),
        )

    @Test
    fun bodyTextPairsMeetAA() {
        for ((name, scheme) in schemes) {
            for ((pair, colors) in textPairs(scheme)) {
                val ratio = contrastRatio(colors.first, colors.second)
                assertTrue("$name $pair = $ratio, needs >= 4.5", ratio >= 4.5)
            }
        }
    }

    @Test
    fun largeTextAndIconPairsMeetThreeToOne() {
        for ((name, scheme) in schemes) {
            for ((pair, colors) in graphicPairs(scheme)) {
                val ratio = contrastRatio(colors.first, colors.second)
                assertTrue("$name $pair = $ratio, needs >= 3.0", ratio >= 3.0)
            }
        }
    }
}
