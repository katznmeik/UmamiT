package com.umamit.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.timer.BrewTimerRoute
import com.umamit.feature.settings.SettingsScreen

/**
 * The app's top-level navigation: the shell with its tabs, and the full-screen
 * timer above it (03 §2.2). A tap on the active brew bar opens the timer; back
 * from the timer keeps the session and returns to the shell (03 §2.3).
 *
 * @param onTimerVisibleChange whether the timer is on screen, for the lock-screen flags (SCR-05).
 */
@Composable
fun UmamitRoot(
    activeBrewSource: ActiveBrewSource,
    modifier: Modifier = Modifier,
    onTimerVisibleChange: (Boolean) -> Unit = {},
) {
    val navController = rememberNavController()
    val motion = UmamiTheme.motion
    val currentOnTimerVisibleChange by rememberUpdatedState(onTimerVisibleChange)
    val closeTimer: () -> Unit = { navController.popBackStack(TimerRoute, inclusive = true) }
    NavHost(
        navController = navController,
        startDestination = ShellRoute,
        modifier = modifier,
        enterTransition = { fadeIn(motion.fade()) },
        exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { fadeOut(motion.exitFade()) },
    ) {
        composable<ShellRoute> {
            UmamitApp(
                activeBrewSource = activeBrewSource,
                onOpenTimer = { navController.navigate(TimerRoute) { launchSingleTop = true } },
            )
        }
        composable<TimerRoute> {
            DisposableEffect(Unit) {
                currentOnTimerVisibleChange(true)
                onDispose { currentOnTimerVisibleChange(false) }
            }
            BrewTimerRoute(
                onClose = closeTimer,
                // M1-13 opens the setup (SCR-04) for the waiting session here.
                onAdjustBrew = closeTimer,
                onOpenTimerSettings = { navController.navigate(TimerSettingsRoute) },
            )
        }
        composable<TimerSettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
