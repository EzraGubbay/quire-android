package com.ezragubbay.quire.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.ezragubbay.quire.BuildConfig
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.feature.auth.PairScreen
import com.ezragubbay.quire.feature.auth.PairViewModel
import com.ezragubbay.quire.feature.auth.scan.PairUriParser
import com.ezragubbay.quire.feature.auth.scan.PairUriResult
import com.ezragubbay.quire.feature.auth.scan.ScanScreen
import com.ezragubbay.quire.push.PushTargetResolver
import kotlinx.serialization.Serializable

/** Pair screen; [server] / [code] arrive from a `quire://pair?server=&code=` deep link. */
@Serializable
data class PairRoute(
    val server: String? = null,
    val code: String? = null,
)

@Serializable
data object ScanRoute

private const val DEEP_LINK_BASE = "quire://pair"

/** The signed-in tab shell (its own nested nav graph lives in [AppShell]). */
@Serializable
data object ShellRoute

/**
 * Root navigation: Pair when signed out, the shell when signed in. The start destination comes from the
 * persisted session; later flips (paired, session expired) navigate and clear the back stack. A tapped notification's
 * target ([AppViewModel.pendingPushTarget]) is handed to the shell, which checks it with [pushTargetResolver].
 */
@Composable
fun QuireApp(
    appViewModel: AppViewModel,
    pairViewModel: PairViewModel,
    pushTargetResolver: PushTargetResolver = PushTargetResolver.Unchecked,
) {
    val signedIn by appViewModel.signedIn.collectAsStateWithLifecycle()
    val pushTarget by appViewModel.pendingPushTarget.collectAsStateWithLifecycle()
    val expired by appViewModel.sessionExpired.collectAsStateWithLifecycle()
    val account by appViewModel.session.collectAsStateWithLifecycle()
    val isSignedIn = signedIn ?: return
    val navController = rememberNavController()

    LaunchedEffect(expired) {
        if (expired) {
            pairViewModel.onSessionExpired()
            appViewModel.onExpiredNoticeShown()
        }
    }

    LaunchedEffect(isSignedIn) {
        val destination = navController.currentDestination ?: return@LaunchedEffect
        val onPair = destination.hasRoute<PairRoute>()
        val target: Any? =
            when {
                isSignedIn && onPair -> ShellRoute
                !isSignedIn && !onPair -> PairRoute()
                else -> null
            }
        if (target != null) {
            navController.navigate(target) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isSignedIn) ShellRoute else PairRoute(),
        // Switch the auth gate (Pair <-> shell) instantly. The default fade keeps the outgoing destination
        // composed for the length of the animation; on session expiry that let the shell's nested NavHost
        // recompose a route-scoped screen (e.g. Courses) and re-resolve its hiltViewModel() AFTER the popped
        // NavBackStackEntry was destroyed, crashing with "cannot access ... ViewModels after ... destroyed".
        // With no exit transition the shell subtree is disposed in one pass, so nothing touches the dead entry.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<PairRoute>(
            deepLinks = listOf(navDeepLink<PairRoute>(basePath = DEEP_LINK_BASE)),
        ) { entry ->
            PairDestination(
                entry = entry,
                pairViewModel = pairViewModel,
                isSignedIn = isSignedIn,
                onSignedIn = appViewModel::onSignedIn,
                onReplaceSessionToShell = {
                    navController.navigate(ShellRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                },
                onScanQr = { navController.navigate(ScanRoute) },
            )
        }
        composable<ScanRoute> {
            ScanScreen(
                onScanned = { server, code ->
                    pairViewModel.onScanned(server, code)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ShellRoute> { entry ->
            // On sign-out / expiry the root pops this entry and routes to Pair. Navigation can still recompose the
            // popped destination's content for a frame; if it does, the shell's nested NavHost would re-run its
            // route-scoped screen (e.g. Courses) and re-resolve its hiltViewModel() on the now-destroyed
            // NavBackStackEntry, crashing with "cannot access ... ViewModels after ... destroyed". Gating on the
            // entry's own lifecycle means the shell (and its nested graph) is never composed once the entry is dead.
            val lifecycleState by entry.lifecycle.currentStateAsState()
            // Also gate on isSignedIn: it flips in the same composition pass that observes sign-out / expiry, so
            // the shell is disposed BEFORE the LaunchedEffect below pops (and destroys) its entry. That closes the
            // race where a nested screen recomposed (e.g. on the 401 that expired the session) on a dead entry.
            if (isSignedIn && lifecycleState != Lifecycle.State.DESTROYED) {
                AppShell(
                    account = account as? SessionState.SignedIn,
                    onSignOut = appViewModel::signOut,
                    pushTarget = pushTarget,
                    pushTargetResolver = pushTargetResolver,
                    onPushTargetConsumed = appViewModel::onPushTargetConsumed,
                )
            }
        }
    }
}

/**
 * The Pair destination: applies a `quire://pair` deep link's prefilled args (validated), then shows the
 * pairing screen. Extracted from [QuireApp] to keep that composable's branching within complexity limits.
 */
@Composable
private fun PairDestination(
    entry: NavBackStackEntry,
    pairViewModel: PairViewModel,
    isSignedIn: Boolean,
    onSignedIn: () -> Unit,
    onReplaceSessionToShell: () -> Unit,
    onScanQr: () -> Unit,
) {
    val (server, code) = entry.toRoute<PairRoute>()
    LaunchedEffect(entry, server, code) {
        if (server != null || code != null) {
            val parsed = PairUriParser.validate(server, code, BuildConfig.ALLOW_CLEARTEXT_LOCAL)
            if (parsed is PairUriResult.Valid) pairViewModel.prefill(parsed.serverUrl, parsed.code)
        }
    }
    PairScreen(
        viewModel = pairViewModel,
        onPaired = {
            // Re-pairing over an existing session: the signed-in flag doesn't flip, so navigate explicitly.
            if (isSignedIn) onReplaceSessionToShell()
            onSignedIn()
        },
        onScanQr = onScanQr,
        replacesSession = isSignedIn,
    )
}
