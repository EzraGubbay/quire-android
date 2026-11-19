package com.ezragubbay.quire.core.designsystem.theme

/**
 * The user's appearance choice (Settings > Appearance, QMOB-E2-S5).
 *
 * [key] is the stable string persisted in DataStore; never rename a key once shipped.
 */
enum class ThemeMode(
    val key: String,
) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    /** Resolve to a concrete light/dark decision given the device's current night-mode state. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            System -> systemDark
            Light -> false
            Dark -> true
        }

    companion object {
        /** Parse a persisted [key]; unknown or missing values fall back to [System]. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}
