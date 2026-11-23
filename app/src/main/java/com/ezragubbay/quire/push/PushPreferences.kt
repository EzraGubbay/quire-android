package com.ezragubbay.quire.push

/**
 * The in-app category toggles. QMOB-E8-S5 (#65) replaces [AllCategoriesOn] with switches stored in DataStore; until
 * then every category is on.
 */
fun interface PushPreferences {
    suspend fun isEnabled(category: PushCategory): Boolean
}

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
