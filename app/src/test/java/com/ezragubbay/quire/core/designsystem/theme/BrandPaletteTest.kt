package com.ezragubbay.quire.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brand palette follows the Quire web app's Folio tokens (`@ezragubbay/folio` `tokens.css`): oxblood accent on
 * parchment, and the matching dark register. Also pins the contrast of the roles the shell chrome draws with.
 */
class BrandPaletteTest {
    private val light = QuireColors.Light.scheme
    private val dark = QuireColors.Dark.scheme
    private val schemes: Map<String, ColorScheme> = mapOf("light" to light, "dark" to dark)

    @Test
    fun lightSchemeUsesFolioOxbloodOnParchment() {
        // --eg-accent / --eg-accent-ink: the primary button fill and its label.
        assertEquals(Color(0xFF6E1E2B), light.primary)
        assertEquals(Color(0xFFF6F1E7), light.onPrimary)
        // --eg-bg: the parchment page.
        assertEquals(Color(0xFFF6F1E7), light.background)
        assertEquals(Color(0xFFF6F1E7), light.surface)
        assertEquals(Color(0xFF1F1B16), light.onSurface)
        assertEquals(Color(0xFF6E1E2B), light.surfaceTint)
    }

    @Test
    fun darkSchemeUsesFolioDarkRegister() {
        assertEquals(Color(0xFFE28A93), dark.primary)
        assertEquals(Color(0xFF1C1915), dark.onPrimary)
        assertEquals(Color(0xFF1C1915), dark.background)
        assertEquals(Color(0xFF1C1915), dark.surface)
        assertEquals(Color(0xFFEEE7DA), dark.onSurface)
        assertEquals(Color(0xFFE28A93), dark.surfaceTint)
    }

    @Test
    fun accentRolesAreNotBlue() {
        for ((name, s) in schemes) {
            val roles =
                mapOf(
                    "primary" to s.primary,
                    "primaryContainer" to s.primaryContainer,
                    "inversePrimary" to s.inversePrimary,
                    "surfaceTint" to s.surfaceTint,
                    "secondaryContainer" to s.secondaryContainer,
                )
            for ((role, c) in roles) {
                assertTrue("$name $role $c reads blue", c.blue <= c.red)
            }
        }
    }

    /** Text and icons the shell draws on brand colours: WCAG AA 4.5:1. */
    @Test
    fun chromeTextMeetsAA() {
        for ((name, s) in schemes) {
            val pairs =
                mapOf(
                    "onPrimary/primary (buttons, New chat, Jump to latest)" to (s.onPrimary to s.primary),
                    "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                    "primary/background (semester header)" to (s.primary to s.background),
                    "nav selected icon onSecondaryContainer/secondaryContainer" to
                        (s.onSecondaryContainer to s.secondaryContainer),
                    "nav selected label secondary/surfaceContainer" to (s.secondary to s.surfaceContainer),
                    "nav unselected onSurfaceVariant/surfaceContainer" to (s.onSurfaceVariant to s.surfaceContainer),
                    "top bar title onSurface/surface" to (s.onSurface to s.surface),
                )
            for ((pair, colors) in pairs) {
                val ratio = contrastRatio(colors.first, colors.second)
                assertTrue("$name $pair = $ratio, needs >= 4.5", ratio >= 4.5)
            }
        }
    }

    /** The bottom-bar indicator pill must stand out from the bar it sits on. */
    @Test
    fun navIndicatorIsVisibleOnTheBar() {
        for ((name, s) in schemes) {
            val ratio = contrastRatio(s.secondaryContainer, s.surfaceContainer)
            assertTrue("$name secondaryContainer/surfaceContainer = $ratio, needs >= 1.2", ratio >= 1.2)
        }
    }
}
