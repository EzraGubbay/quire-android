package com.ezragubbay.quire.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun systemFollowsDevice() {
        assertTrue(ThemeMode.System.isDark(systemDark = true))
        assertFalse(ThemeMode.System.isDark(systemDark = false))
    }

    @Test
    fun explicitModesIgnoreDevice() {
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
    }

    @Test
    fun storageKeysRoundTrip() {
        for (mode in ThemeMode.entries) assertEquals(mode, ThemeMode.fromKey(mode.key))
    }

    @Test
    fun unknownOrMissingKeyFallsBackToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.System, ThemeMode.fromKey("sepia"))
    }
}
