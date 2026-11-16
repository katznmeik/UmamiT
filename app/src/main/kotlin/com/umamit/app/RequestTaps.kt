package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.StateFlow

/**
 * Each tap of [request] once, for [CatalogRefreshTaps] and [startTaps]: with the setup done and no setup on screen,
 * [show] opens what the tap is for; during an alert it waits until the alert has ended (rule 8); before the setup is
 * done the tap is let go (ONB-03). Either way [onConsume] then takes the token.
 */
@Composable
@Suppress("LongParameterList") // the navigation, the two gates, the request, its consumer and what it shows
internal fun RequestTaps(
    navController: NavHostController,
    alert: String?,
    setupDone: Boolean,
    request: StateFlow<Long>,
    onConsume: (Long) -> Unit,
    show: (Long) -> Unit,
) {
    var handled by rememberSaveable { mutableLongStateOf(0L) }
    val token by request.collectAsStateWithLifecycle()
    val currentOnConsume by rememberUpdatedState(onConsume)
    val currentShow by rememberUpdatedState(show)
    LaunchedEffect(token, alert, setupDone) {
        if (token == 0L || token == handled) return@LaunchedEffect
        val inSetup = navController.currentDestination?.hasRoute<OnboardingRoute>() == true
        if (setupDone && !inSetup) {
            if (alert != null) return@LaunchedEffect
            currentShow(token)
        }
        handled = token
        currentOnConsume(token)
    }
}
