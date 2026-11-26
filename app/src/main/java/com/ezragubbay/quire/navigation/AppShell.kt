package com.ezragubbay.quire.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ezragubbay.quire.APP_ROOT_TAG
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.feature.chat.ChatsScreen
import com.ezragubbay.quire.feature.chat.conversation.ConversationScreen
import com.ezragubbay.quire.feature.chat.conversation.DocumentHeader
import com.ezragubbay.quire.feature.chat.conversation.toViewerRoute
import com.ezragubbay.quire.feature.chat.threads.CourseThreadsScreen
import com.ezragubbay.quire.feature.courses.detail.CourseScreen
import com.ezragubbay.quire.feature.courses.list.CoursesScreen
import com.ezragubbay.quire.feature.downloads.DownloadsScreen
import com.ezragubbay.quire.feature.notes.NoteReaderScreen
import com.ezragubbay.quire.feature.search.SearchScreen
import com.ezragubbay.quire.feature.settings.SettingsScreen
import com.ezragubbay.quire.feature.viewer.pdf.PdfViewerScreen
import com.ezragubbay.quire.push.PushTarget
import com.ezragubbay.quire.push.PushTargetMissing
import com.ezragubbay.quire.push.PushTargetResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ACTION_SETTINGS_TAG = "action-settings"
const val ACTION_SEARCH_TAG = "action-search"
const val PUSH_TARGET_MISSING_TAG = "push-target-missing"

/**
 * The app shell: top bar (Settings action), bottom bar (Courses, Downloads, Chats) and the nav host.
 * Settings is pushed over the shell with its own top bar (back arrow) instead of this chrome.
 *
 * A tapped notification's [pushTarget] is checked with [pushTargetResolver], opened along its back stack
 * (Courses → course → file / note) and then reported with [onPushTargetConsumed] (ADR 0003 §4.5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    account: SessionState.SignedIn? = null,
    onSignOut: (removeDownloads: Boolean) -> Unit = {},
    downloadBytes: Long = 0L,
    pushTarget: PushTarget? = null,
    pushTargetResolver: PushTargetResolver = PushTargetResolver.Unchecked,
    onPushTargetConsumed: (PushTarget) -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val snackbar = remember { SnackbarHostState() }
    PushTargetEffect(pushTarget, pushTargetResolver, navController, snackbar, onPushTargetConsumed)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // Settings, a course and search bring their own top bar; the shell chrome is hidden for them.
    val onSettings =
        destination?.hasRoute<SettingsRoute>() == true ||
            destination?.hasRoute<CourseRoute>() == true ||
            destination?.hasRoute<SearchRoute>() == true ||
            destination?.hasRoute<ViewerRoute>() == true ||
            destination?.hasRoute<NoteReaderRoute>() == true ||
            destination?.hasRoute<CourseThreadsRoute>() == true ||
            destination?.hasRoute<ConversationRoute>() == true
    val current = TopLevelDestination.entries.firstOrNull { destination?.hasRoute(it.route::class) == true }

    Scaffold(
        modifier = Modifier.testTag(APP_ROOT_TAG),
        snackbarHost = {
            // The shell's only snackbar: a tapped notification whose file, note or course is gone.
            SnackbarHost(snackbar) { Snackbar(it, Modifier.testTag(PUSH_TARGET_MISSING_TAG)) }
        },
        topBar = {
            // Settings brings its own large top bar (with back).
            if (!onSettings) {
                TopAppBar(
                    // Bold here only: titleLarge's SemiBold renders thin in some device system fonts.
                    title = { Text(current?.label.orEmpty(), fontWeight = FontWeight.Bold) },
                    actions = {
                        if (current == TopLevelDestination.Courses) {
                            IconButton(
                                onClick = { navController.navigate(SearchRoute) { launchSingleTop = true } },
                                modifier = Modifier.testTag(ACTION_SEARCH_TAG),
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.courses_search))
                            }
                        }
                        IconButton(
                            onClick = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                            modifier = Modifier.testTag(ACTION_SETTINGS_TAG),
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!onSettings) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == current,
                            onClick = { navController.navigateToTopLevel(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.navTag),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Start.route,
            modifier = Modifier.padding(padding),
        ) {
            composable<CoursesRoute> {
                CoursesScreen(onOpenCourse = { navController.navigate(CourseRoute(it)) })
            }
            composable<CourseRoute> { entry ->
                val slug = entry.toRoute<CourseRoute>().slug
                CourseScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFile = { navController.navigate(ViewerRoute(slug, it)) },
                    onOpenNote = { navController.navigate(NoteReaderRoute(slug, it)) },
                    onAsk = { navController.navigate(CourseThreadsRoute(slug)) },
                )
            }
            composable<CourseThreadsRoute> {
                CourseThreadsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenThread = { slug, id -> navController.navigate(ConversationRoute(slug, id)) },
                )
            }
            composable<ViewerRoute> {
                PdfViewerScreen(
                    onBack = { navController.popBackStack() },
                    onAskAboutDocument = { slug, thread, title ->
                        navController.navigate(ConversationRoute(slug, thread, title))
                    },
                )
            }
            composable<NoteReaderRoute> { NoteReaderScreen(onBack = { navController.popBackStack() }) }
            composable<ConversationRoute> { entry ->
                val route = entry.toRoute<ConversationRoute>()
                val documentTitle = route.documentTitle
                ConversationScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCitation = { c -> c.toViewerRoute(route.courseSlug)?.let { navController.navigate(it) } },
                    documentHeader = documentTitle?.let { { DocumentHeader(it) } },
                )
            }
            composable<SearchRoute> {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCourse = { navController.navigate(CourseRoute(it)) },
                )
            }
            composable<DownloadsRoute> { DownloadsScreen() }
            composable<ChatsRoute> {
                ChatsScreen(onOpenThread = { slug, id -> navController.navigate(ConversationRoute(slug, id)) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    account = account,
                    onSignOut = onSignOut,
                    downloadBytes = downloadBytes,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Consumes a tapped notification's [target] once: checks it, opens what is still there and says what is not. The
 * target is reported consumed only after navigating, so a recomposition or recreate mid-check simply checks again.
 */
@Composable
internal fun PushTargetEffect(
    target: PushTarget?,
    resolver: PushTargetResolver,
    navController: NavHostController,
    snackbar: SnackbarHostState,
    onConsumed: (PushTarget) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val fileGone = stringResource(R.string.push_target_missing_file)
    val noteGone = stringResource(R.string.push_target_missing_note)
    val courseGone = stringResource(R.string.push_target_missing_course)
    val messages =
        remember(fileGone, noteGone, courseGone) {
            mapOf(
                PushTargetMissing.File to fileGone,
                PushTargetMissing.Note to noteGone,
                PushTargetMissing.Course to courseGone,
            )
        }
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        val resolution = resolver.resolve(target)
        // The check finishes on a network thread and nothing guarantees the effect resumes on the main thread (the
        // Compose test clock resumes it in place), but NavController must only be touched there.
        withContext(Dispatchers.Main.immediate) {
            // The nested graph is set during the first layout pass; wait for its start destination before navigating.
            navController.currentBackStackEntryFlow.first()
            navController.openPushTarget(resolution.target)
            // Outlives this effect, which restarts (and is cancelled) once the target is consumed.
            resolution.missing?.let { missing -> scope.launch { snackbar.showSnackbar(messages.getValue(missing)) } }
            onConsumed(target)
        }
    }
}

/**
 * Pops the shell back to Courses (kept, with its ViewModel), then pushes the target's course and file / note so back
 * walks out the same way.
 */
private fun NavHostController.openPushTarget(target: PushTarget) {
    val stack = target.backStack()
    navigate(stack.first()) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
    stack.drop(1).forEach { navigate(it) }
}

/** Switches tab keeping each tab's back stack and state; Courses stays at the bottom so back returns to it. */
private fun NavHostController.navigateToTopLevel(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
