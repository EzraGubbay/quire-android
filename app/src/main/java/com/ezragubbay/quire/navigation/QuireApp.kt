package com.ezragubbay.quire.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ezragubbay.quire.core.data.session.SessionState
import com.ezragubbay.quire.feature.auth.PairScreen
import com.ezragubbay.quire.feature.auth.PairViewModel
import kotlinx.serialization.Serializable

@Serializable
data object PairRoute

/** The signed-in tab shell (its own nested nav graph lives in [AppShell]). */
@Serializable
data object ShellRoute

/**
 * Root navigation: Pair when signed out, the shell when signed in. The start destination comes from the
 * persisted session; later flips (paired, session expired) navigate and clear the back stack.
 */
@Composable
fun QuireApp(
    appViewModel: AppViewModel,
    pairViewModel: PairViewModel,
) {
    val signedIn by appViewModel.signedIn.collectAsStateWithLifecycle()
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
                !isSignedIn && !onPair -> PairRoute
                else -> null
            }
        if (target != null) {
            navController.navigate(target) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = if (isSignedIn) ShellRoute else PairRoute) {
        composable<PairRoute> { PairScreen(viewModel = pairViewModel, onPaired = appViewModel::onSignedIn) }
        composable<ShellRoute> {
            AppShell(account = account as? SessionState.SignedIn, onSignOut = appViewModel::signOut)
        }
    }
}
