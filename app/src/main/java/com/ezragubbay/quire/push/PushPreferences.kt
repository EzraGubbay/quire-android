package com.ezragubbay.quire.push

/** The in-app category toggles; the app binds [NotificationPreferencesStore] (Settings → Notifications). */
fun interface PushPreferences {
    suspend fun isEnabled(category: PushCategory): Boolean
}

/** Every category on: the default before the user changes a switch, and for tests. */
object AllCategoriesOn : PushPreferences {
    override suspend fun isEnabled(category: PushCategory) = true
}

/**
 * The categories to register (ADR 0003 §4.4, §4.6): none while notifications are denied or turned off for the app,
 * so the server stops sending; otherwise the categories the user has on.
 */
class PushCategoryPolicy(
    private val notificationsAllowed: () -> Boolean,
    private val preferences: PushPreferences = AllCategoriesOn,
) {
    suspend fun current(): List<PushCategory> =
        if (notificationsAllowed()) PushCategory.entries.filter { preferences.isEnabled(it) } else emptyList()
}
