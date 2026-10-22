package com.umamit.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.timer.BrewTimerRoute
import com.umamit.feature.settings.SettingsScreen

/**
 * The app's top-level navigation: the shell with its tabs, and the full-screen
 * timer above it (03 §2.2). A tap on the active brew bar opens the timer; back
 * from the timer keeps the session and returns to the shell (03 §2.3).
 *
 * Alerts first (rule 8, D-058): no route transition runs into or out of the
 * timer while the alert plays, and *Timer-Einstellungen* opened before the
 * alert is popped back to the timer in the frame the alert starts.
 *
 * @param onTimerVisibleChange whether the timer is the current route, for the lock-screen flags (SCR-05).
 * @param timerScreen the timer route's content; tests replace it, the app uses [BrewTimerRoute].
 */
@Composable
fun UmamitRoot(
    activeBrewSource: ActiveBrewSource,
    modifier: Modifier = Modifier,
    onTimerVisibleChange: (Boolean) -> Unit = {},
    timerScreen: @Composable (onClose: () -> Unit, onOpenTimerSettings: () -> Unit) -> Unit = { onClose, onSettings ->
        // M1-13 opens the setup (SCR-04) for the waiting session from *Brühen anpassen*.
        BrewTimerRoute(onClose = onClose, onAdjustBrew = onClose, onOpenTimerSettings = onSettings)
    },
) {
    val navController = rememberNavController()
    val motion = UmamiTheme.motion
    val brew by activeBrewSource.activeBrew.collectAsStateWithLifecycle()
    val alerting = brew?.alerting == true
    val currentAlerting by rememberUpdatedState(alerting)
    // Whether *Timer-Einstellungen* was opened while the alert already played; then it may stay.
    var settingsOpenedAlerting by rememberSaveable { mutableStateOf(false) }

    val entry by navController.currentBackStackEntryAsState()
    val timerOnTop = entry?.destination?.hasRoute<TimerRoute>() == true
    val currentOnTimerVisibleChange by rememberUpdatedState(onTimerVisibleChange)
    // From the back stack, not the route's composition: it changes when navigation starts, not after the fade.
    LaunchedEffect(timerOnTop) { currentOnTimerVisibleChange(timerOnTop) }

    LaunchedEffect(alerting) {
        val settingsOnTop = navController.currentDestination?.hasRoute<TimerSettingsRoute>() == true
        if (alerting && settingsOnTop && !settingsOpenedAlerting) {
            navController.popBackStack(TimerRoute, inclusive = false)
        }
    }

    // No transition at all where an alerting timer enters, leaves or is uncovered (`alertEnter`).
    fun AnimatedTimer.instant(): Boolean = currentAlerting && (initial.isTimer() || target.isTimer())
    val closeTimer: () -> Unit = { navController.popBackStack(TimerRoute, inclusive = true) }
    NavHost(
        navController = navController,
        startDestination = ShellRoute,
        modifier = modifier,
        enterTransition = {
            if (AnimatedTimer(initialState, targetState).instant()) EnterTransition.None else fadeIn(motion.fade())
        },
        exitTransition = {
            if (AnimatedTimer(
                    initialState,
                    targetState,
                ).instant()
            ) {
                ExitTransition.None
            } else {
                ExitTransition.KeepUntilTransitionsFinished
            }
        },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            if (AnimatedTimer(initialState, targetState).instant()) ExitTransition.None else fadeOut(motion.exitFade())
        },
    ) {
        composable<ShellRoute> {
            UmamitApp(
                activeBrewSource = activeBrewSource,
                onOpenTimer = { navController.navigate(TimerRoute) { launchSingleTop = true } },
            )
        }
        composable<TimerRoute> {
            timerScreen(closeTimer) {
                settingsOpenedAlerting = currentAlerting
                navController.navigate(TimerSettingsRoute)
            }
        }
        composable<TimerSettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}

/** The two ends of a route transition. */
private class AnimatedTimer(val initial: NavBackStackEntry, val target: NavBackStackEntry)

private fun NavBackStackEntry.isTimer(): Boolean = destination.hasRoute<TimerRoute>()
