package com.ezragubbay.quire.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.ezragubbay.quire.core.designsystem.icon.QuireIcons
import kotlinx.serialization.Serializable

/** Type-safe navigation routes. */
@Serializable
data object CoursesRoute

@Serializable
data object DownloadsRoute

@Serializable
data object ChatsRoute

@Serializable
data object SettingsRoute

/** The three bottom-bar destinations, in display order. Test tags follow `docs/e2e/E2-shell.md`. */
enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val navTag: String,
    val screenTag: String,
) {
    Courses(CoursesRoute, "Courses", Icons.Filled.Home, "nav-courses", "screen-courses"),
    Downloads(DownloadsRoute, "Downloads", QuireIcons.ArchiveBoxArrowDown, "nav-downloads", "screen-downloads"),
    Chats(ChatsRoute, "Chats", Icons.AutoMirrored.Filled.Send, "nav-chats", "screen-chats"),
    ;

    companion object {
        /** Where the app opens, and where system back returns to from the other tabs. */
        val Start: TopLevelDestination = Courses

        /** The top-level destination owning [route], or null (Settings, unknown routes). */
        fun fromRoute(route: Any?): TopLevelDestination? = entries.firstOrNull { it.route == route }
    }
}
