package com.umamit.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.umamit.core.designsystem.theme.SteepingColorScheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.timer.BrewTimerRoute
import com.umamit.feature.journal.JournalSources
import com.umamit.feature.settings.SettingsScreen
import com.umamit.feature.settings.diagnostics.DiagnosticsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's top-level navigation: the shell with its tabs, and the full-screen
 * timer above it (03 §2.2). A tap on the active brew bar opens the timer; back
 * from the timer keeps the session and returns to the shell (03 §2.3).
 *
 * Alerts first (rule 8, D-058): no route transition runs into or out of the
 * timer while the alert plays, and *Timer-Einstellungen* opened before the
 * alert is popped back to the timer in the frame the alert starts.
 *
 * A notification opens the timer (03 §2.4; #47): each new [timerRequest] token pops whatever
 * lies above the timer, or opens it. Until the timer is on top, the root draws the timer's dark
 * background over the shell and reports the timer as visible, so the shell never shows over
 * the lock screen, not even for one frame (D-058, D-064).
 *
 * @param journalSources the journal tab and the „Gespeichert" sheet, which never shows over an alert (M1-16).
 * @param onTimerVisibleChange whether the timer is the current route, for the lock-screen flags (SCR-05).
 * @param alertKey the identity of the alert playing now (`TimerUiState.alertKey`), or null.
 * @param timerRequest a new non-zero token for each request to open the timer (a notification's
 *   content or full-screen intent); 0 for none.
 * @param timerScreen the timer route's content; tests replace it, the app uses [BrewTimerRoute].
 * @param diagnosticsScreen *Diagnose* opened from *Timer-Einstellungen*; tests replace it, the app uses
 *   [DiagnosticsRoute].
 */
@Composable
fun UmamitRoot(
    activeBrewSource: ActiveBrewSource,
    journalSources: JournalSources,
    alertKey: StateFlow<String?>,
    modifier: Modifier = Modifier,
    timerRequest: StateFlow<Long> = NO_TIMER_REQUEST,
    onTimerVisibleChange: (Boolean) -> Unit = {},
    timerScreen: @Composable (onClose: () -> Unit, onOpenTimerSettings: () -> Unit) -> Unit = { onClose, onSettings ->
        // M1-13 opens the setup (SCR-04) for the waiting session from *Brühen anpassen*.
        BrewTimerRoute(onClose = onClose, onAdjustBrew = onClose, onOpenTimerSettings = onSettings)
    },
    diagnosticsScreen: @Composable (onBack: () -> Unit) -> Unit = { onBack -> DiagnosticsRoute(onBack = onBack) },
) {
    val navController = rememberNavController()
    val motion = UmamiTheme.motion
    // Only the alert's identity is read here, so the root recomposes per alert, not per second.
    val alert by alertKey.collectAsStateWithLifecycle()
    val currentAlert by rememberUpdatedState(alert)
    val currentAlerting by rememberUpdatedState(alert != null)
    // The alert *Timer-Einstellungen* was opened in (null: none played). Only that alert may stay under it.
    var settingsAlert by rememberSaveable { mutableStateOf<String?>(null) }

    val entry by navController.currentBackStackEntryAsState()
    val timerOnTop = entry?.destination?.hasRoute<TimerRoute>() == true
    val requestPending = openTimerOnRequest(navController, timerRequest)
    val timerShown = timerOnTop || requestPending
    val currentOnTimerVisibleChange by rememberUpdatedState(onTimerVisibleChange)
    // From the back stack, not the route's composition: it changes when navigation starts, not after the fade.
    LaunchedEffect(timerShown) { currentOnTimerVisibleChange(timerShown) }

    // Each new alert pops *Timer-Einstellungen* opened before it, once (rule 8). Checked on every new alert
    // and on resume, so an alert that started while the app was in the background is caught too.
    val popSettingsForNewAlert = {
        // *Timer-Einstellungen* or *Diagnose* opened from it: everything above the timer goes (rule 8).
        val settingsOnTop = navController.currentDestination?.let {
            it.hasRoute<TimerSettingsRoute>() || it.hasRoute<DiagnosticsRoute>()
        } == true
        val playing = currentAlert
        if (playing != null && settingsOnTop && playing != settingsAlert) {
            navController.popBackStack(TimerRoute, inclusive = false)
        }
    }
    LaunchedEffect(alert) { popSettingsForNewAlert() }
    // A defensive belt: the keyed effect above already sees a new alert after the background, because the
    // collected key resumes with it. This also checks once on every resume, should that ever change.
    LifecycleResumeEffect(Unit) {
        popSettingsForNewAlert()
        onPauseOrDispose {}
    }

    // No transition at all where an alerting timer enters, leaves or is uncovered (`alertEnter`).
    fun AnimatedTimer.instant(): Boolean = currentAlerting && (initial.isTimer() || target.isTimer())
    val closeTimer: () -> Unit = { navController.popBackStack(TimerRoute, inclusive = true) }
    Box(modifier) {
        NavHost(
            navController = navController,
            startDestination = ShellRoute,
            modifier = Modifier.fillMaxSize(),
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
                if (AnimatedTimer(
                        initialState,
                        targetState,
                    ).instant()
                ) {
                    ExitTransition.None
                } else {
                    fadeOut(motion.exitFade())
                }
            },
        ) {
            composable<ShellRoute> {
                UmamitApp(
                    activeBrewSource = activeBrewSource,
                    journalSources = journalSources,
                    alerting = alert != null,
                    onOpenTimer = { navController.navigate(TimerRoute) { launchSingleTop = true } },
                )
            }
            composable<TimerRoute> {
                timerScreen(closeTimer) {
                    settingsAlert = currentAlert
                    navController.navigate(TimerSettingsRoute)
                }
            }
            composable<TimerSettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDiagnostics = { navController.navigate(DiagnosticsRoute) },
                )
            }
            composable<DiagnosticsRoute> { diagnosticsScreen { navController.popBackStack() } }
        }
        ShellCover(requestPending = requestPending, timerOnTop = timerOnTop)
    }
}

/**
 * Opens the timer for each new [timerRequest] token (a notification, #47): whatever lies above
 * the timer (*Timer-Einstellungen*, *Diagnose*) is popped, or the timer is opened. Returns
 * whether a request waits for that, from the first composition on. The handled token survives a
 * configuration change, so a recreated activity does not open the timer again.
 */
@Composable
private fun openTimerOnRequest(navController: NavHostController, timerRequest: StateFlow<Long>): Boolean {
    val request by timerRequest.collectAsStateWithLifecycle()
    var handledRequest by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(request) {
        if (request != 0L && request != handledRequest) {
            if (!navController.popBackStack(TimerRoute, inclusive = false)) {
                navController.navigate(TimerRoute) { launchSingleTop = true }
            }
            handledRequest = request
        }
    }
    return request != 0L && request != handledRequest
}

/** Opened from a notification, the timer comes next: its dark background covers the shell until then. */
@Composable
private fun ShellCover(requestPending: Boolean, timerOnTop: Boolean) {
    if (requestPending && !timerOnTop) {
        Box(Modifier.fillMaxSize().background(SteepingColorScheme.background))
    }
}

/** No request to open the timer ever comes. */
private val NO_TIMER_REQUEST: StateFlow<Long> = MutableStateFlow(0L)

/** The two ends of a route transition. */
private class AnimatedTimer(val initial: NavBackStackEntry, val target: NavBackStackEntry)

private fun NavBackStackEntry.isTimer(): Boolean = destination.hasRoute<TimerRoute>()
