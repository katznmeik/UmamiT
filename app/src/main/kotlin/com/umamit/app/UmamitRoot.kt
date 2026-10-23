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
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.umamit.core.designsystem.theme.SteepingColorScheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.timer.BrewTimerRoute
import com.umamit.feature.journal.JournalSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.umamit.feature.brew.quick.QuickTimerRoute as QuickTimerScreenRoute
import com.umamit.feature.brew.setup.BrewSetupRoute as BrewSetupScreenRoute
import com.umamit.feature.onboarding.OnboardingRoute as OnboardingScreenRoute

/**
 * The app's top-level navigation: the shell with its tabs, and the full-screen
 * timer above it (03 §2.2). A tap on the active brew bar opens the timer; back
 * from the timer keeps the session and returns to the shell (03 §2.3).
 *
 * Alerts first (rule 8, D-058, D-062): no route transition runs into or out
 * of the timer while the alert plays, and every full-screen route above the
 * shell or the timer (the setup, *Timer-Einstellungen*, *Über*, *Diagnose*,
 * the first-start setup and any later one) that was opened before the alert gives way to the timer in the
 * frame the alert starts: popped back to the timer when it is on the stack,
 * else replaced by it, without a transition. The quick timer (SCR-07) is such a
 * route until it runs; then the timer shows it.
 *
 * A notification opens the timer the same way (03 §2.4; D-064): each new [timerRequest] token
 * (its content or full-screen intent) brings the timer to the top. Until it is there, the root
 * draws the timer's dark background over the shell and reports the timer as visible, so the
 * shell never shows over the lock screen, not even for one frame (D-058). One place decides
 * both ([rememberTimerFirst]).
 *
 * @param journalSources the journal tab and the „Gespeichert" sheet, which never shows over an alert (M1-16).
 * @param onTimerVisibleChange whether the timer is the current route, for the lock-screen flags (SCR-05).
 * @param alertKey the identity of the alert playing now (`TimerUiState.alertKey`), or null.
 * @param timerRequest a new non-zero token for each request to open the timer (a notification's
 *   content or full-screen intent); 0 for none.
 * @param onConsumeTimerRequest consumes that token: the timer was opened for it.
 * @param timerScreen the timer route's content; tests replace it, the app uses [BrewTimerRoute].
 *   Its third argument opens the setup of the waiting session (*Brühen anpassen*, BRW-11).
 * @param startScreen the Start tab's content; the app passes [StartTabContent].
 * @param setupScreen the setup route's content (SCR-04); tests replace it.
 * @param onboardingCompleted whether the first-start setup is done (ONB-04); null until the settings are
 *   read, and nothing is shown until then. Not done: the setup (SCR-01) is the start destination.
 * @param onboardingScreen the setup's content; tests replace it, the app uses [OnboardingScreenRoute].
 * @param settings the settings, *Über* and *Diagnose* (SCR-25, SCR-27), in the tabs and above the timer;
 *   tests replace them.
 * @param quickTimerScreen the quick timer route's content (SCR-07); tests replace it. Its second argument
 *   opens the timer once the quick timer runs.
 */
@Composable
internal fun UmamitRoot(
    activeBrewSource: ActiveBrewSource,
    journalSources: JournalSources,
    alertKey: StateFlow<String?>,
    modifier: Modifier = Modifier,
    timerRequest: StateFlow<Long> = NO_TIMER_REQUEST,
    onConsumeTimerRequest: (Long) -> Unit = {},
    onTimerVisibleChange: (Boolean) -> Unit = {},
    timerScreen: @Composable (
        onClose: () -> Unit,
        onOpenTimerSettings: () -> Unit,
        onAdjustBrew: (sessionId: String) -> Unit,
    ) -> Unit = { onClose, onSettings, onAdjust ->
        BrewTimerRoute(onClose = onClose, onAdjustBrew = onAdjust, onOpenTimerSettings = onSettings)
    },
    startScreen: @Composable (StartTab) -> Unit = { PlaceholderStart(it) },
    setupScreen: @Composable (onBack: () -> Unit, onOpenTimer: () -> Unit) -> Unit = { onBack, onOpenTimer ->
        BrewSetupScreenRoute(onBack = onBack, onOpenTimer = onOpenTimer)
    },
    onboardingCompleted: StateFlow<Boolean?> = MutableStateFlow(true),
    onboardingScreen: @Composable (onFinish: () -> Unit) -> Unit = { onFinish ->
        OnboardingScreenRoute(onFinish = onFinish)
    },
    settings: SettingsScreens = SettingsScreens(),
    quickTimerScreen: @Composable (onBack: () -> Unit, onOpenTimer: () -> Unit) -> Unit = { onBack, onOpenTimer ->
        QuickTimerScreenRoute(onBack = onBack, onOpenTimer = onOpenTimer)
    },
) {
    val navController = rememberNavController()
    // The start destination, chosen once from the first value read: the setup until it is completed. Nothing
    // below runs before it is known, so no navigation can reach a NavHost without its graph.
    val completed by onboardingCompleted.collectAsStateWithLifecycle()
    var setupFirst by rememberSaveable { mutableStateOf<Boolean?>(null) }
    if (setupFirst == null) setupFirst = completed?.not()
    val startWithSetup = setupFirst ?: return
    val motion = UmamiTheme.motion
    // Only the alert's identity is read here, so the root recomposes per alert, not per second.
    val alert by alertKey.collectAsStateWithLifecycle()
    val currentAlerting by rememberUpdatedState(alert != null)
    val timerFirst = rememberTimerFirst(navController, alert, timerRequest, onConsumeTimerRequest)

    val entry by navController.currentBackStackEntryAsState()
    val timerOnTop = entry?.destination?.hasRoute<TimerRoute>() == true
    val timerShown = timerOnTop || timerFirst.requestPending
    val currentOnTimerVisibleChange by rememberUpdatedState(onTimerVisibleChange)
    // From the back stack, not the route's composition: it changes when navigation starts, not after the fade.
    LaunchedEffect(timerShown) { currentOnTimerVisibleChange(timerShown) }
    val navigateAbove = timerFirst.navigateAbove
    // *Einrichtung erneut zeigen* (ONB-04): above the shell or the timer, so it gives way to an alert too.
    val rerunSetup: () -> Unit = { navigateAbove(OnboardingRoute) { launchSingleTop = true } }
    val finishSetup: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            navController.navigate(ShellRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
        }
    }

    // No transition at all where an alerting timer enters, leaves or is uncovered (`alertEnter`).
    fun AnimatedTimer.instant(): Boolean = currentAlerting && (initial.isTimer() || target.isTimer())
    val closeTimer: () -> Unit = { navController.popBackStack(TimerRoute, inclusive = true) }
    val openTimer: () -> Unit = { navController.navigate(TimerRoute) { launchSingleTop = true } }
    val startNavigation = StartNavigation(
        // During an alert a tea tap (*Favoriten*, the picker) opens the alerting timer, never a setup that
        // would cover it (rule 8, D-062 S3).
        openSetup = { teaId ->
            if (currentAlerting) {
                openTimer()
            } else {
                navigateAbove(BrewSetupRoute(teaId = teaId)) {
                    launchSingleTop = true
                }
            }
        },
        openTimer = openTimer,
        // A route above the shell like the setup: it gives way to a new alert (rule 8). During an alert,
        // *Schnelltimer* and *Ohne Tee (Schnelltimer)* open the alerting timer instead (D-062 S3, D-065).
        openQuickTimer = {
            if (currentAlerting) {
                openTimer()
            } else {
                navigateAbove(QuickTimerRoute) { launchSingleTop = true }
            }
        },
    )
    Box(modifier) {
        NavHost(
            navController = navController,
            startDestination = if (startWithSetup) OnboardingRoute else ShellRoute,
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
                    onOpenTimer = openTimer,
                    startNavigation = startNavigation,
                    alertKey = alert,
                    startScreen = startScreen,
                    settings = settings.withRerunSetup(rerunSetup),
                )
            }
            composable<OnboardingRoute> { onboardingScreen(finishSetup) }
            composable<TimerRoute> {
                timerScreen(
                    closeTimer,
                    { navigateAbove(TimerSettingsRoute) {} },
                    { sessionId ->
                        // The setup replaces the timer; *Brühen starten* there opens it again (BRW-11).
                        navigateAbove(BrewSetupRoute(sessionId = sessionId)) {
                            popUpTo<TimerRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<BrewSetupRoute> {
                setupScreen(
                    { navController.popBackStack() },
                    // The timer replaces the setup, so back from the timer returns to where the brew began.
                    {
                        navController.navigate(TimerRoute) {
                            popUpTo<BrewSetupRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<QuickTimerRoute> {
                quickTimerScreen(
                    { navController.popBackStack() },
                    // The timer replaces it, so back from the running quick timer returns to Start.
                    {
                        navController.navigate(TimerRoute) {
                            popUpTo<QuickTimerRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<TimerSettingsRoute> {
                settings.screen(
                    SettingsHost(
                        onBack = { navController.popBackStack() },
                        onOpenAbout = { navigateAbove(AboutRoute) {} },
                        alertKey = alert,
                    ),
                )
            }
            composable<AboutRoute> {
                settings.about(
                    AboutHost(
                        onBack = { navController.popBackStack() },
                        onRerunSetup = rerunSetup,
                        onOpenDiagnostics = { navigateAbove(DiagnosticsRoute) {} },
                    ),
                )
            }
            composable<DiagnosticsRoute> { settings.diagnostics { navController.popBackStack() } }
        }
        ShellCover(requestPending = timerFirst.requestPending, timerOnTop = timerOnTop)
    }
    // The bars' one owner (D-069), from the back stack: it changes when navigation starts, so screens
    // overlapping in a crossfade never fight over it. Last, so it shifts no saveable key above.
    SystemBarIcons(
        systemBarDarkIcons(
            timerOnTop = timerShown,
            quickTimerOnTop = entry?.destination?.hasRoute<QuickTimerRoute>() == true,
            alerting = alert != null,
            darkTheme = UmamiTheme.isDark,
        ),
    )
}

/** What [rememberTimerFirst] gives the root. */
private class TimerFirst(
    /** A notification's request waits for the timer to be on top. */
    val requestPending: Boolean,
    /** Opens a route above the shell or the timer, remembering the alert it is opened in. */
    val navigateAbove: (route: Any, options: NavOptionsBuilder.() -> Unit) -> Unit,
)

/**
 * The one place that puts the timer on top now (rule 8; D-058, D-062, D-064):
 * - **a new alert:** a route above the shell or the timer (the setup, *Timer-Einstellungen*,
 *   *Über*, *Diagnose*, the first-start setup and any later one) opened before it gives way to
 *   the timer, once; one opened in this alert stays. Checked on every new alert and on resume,
 *   so an alert that started while the app was in the background is caught too;
 * - **a notification's request** (a new [timerRequest] token): the timer comes to the top, and
 *   the activity forgets the token ([onConsume]) only then, so a recreation before keeps it.
 *
 * Either way the timer is popped back to when it is on the stack, else it replaces what lies
 * above the shell, without a transition while alerting. The alert a route was opened in and the
 * handled token survive a configuration change.
 */
@Composable
private fun rememberTimerFirst(
    navController: NavHostController,
    alert: String?,
    timerRequest: StateFlow<Long>,
    onConsume: (Long) -> Unit,
): TimerFirst {
    // The alert the route on top of the shell or timer was opened in (null: none played). Only that alert
    // may stay under it.
    var aboveAlert by rememberSaveable { mutableStateOf<String?>(null) }
    var handledRequest by rememberSaveable { mutableLongStateOf(0L) }
    val request by timerRequest.collectAsStateWithLifecycle()
    val currentAlert by rememberUpdatedState(alert)
    val currentOnConsume by rememberUpdatedState(onConsume)

    val bringTimerToTop = {
        if (!navController.popBackStack(TimerRoute, inclusive = false)) {
            navController.navigate(TimerRoute) {
                popUpTo<ShellRoute> { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    val giveWayToNewAlert = {
        val playing = currentAlert
        val above = navController.currentDestination?.isAboveShellAndTimer() == true
        if (playing != null && above && playing != aboveAlert) bringTimerToTop()
    }
    LaunchedEffect(alert) { giveWayToNewAlert() }
    // A defensive belt: the keyed effect above already sees a new alert after the background, because the
    // collected key resumes with it. This also checks once on every resume, should that ever change.
    LifecycleResumeEffect(Unit) {
        giveWayToNewAlert()
        onPauseOrDispose {}
    }
    LaunchedEffect(request) {
        if (request != 0L && request != handledRequest) {
            bringTimerToTop()
            handledRequest = request
            currentOnConsume(request)
        }
    }
    return TimerFirst(
        requestPending = request != 0L && request != handledRequest,
        navigateAbove = { route, options ->
            aboveAlert = currentAlert
            navController.navigate(route, options)
        },
    )
}

/** Opened from a notification, the timer comes next: its dark background covers the shell until then. */
@Composable
private fun ShellCover(requestPending: Boolean, timerOnTop: Boolean) {
    if (requestPending && !timerOnTop) {
        Box(Modifier.fillMaxSize().background(SteepingColorScheme.background).testTag(SHELL_COVER_TAG))
    }
}

/** The cover over the shell while the timer opens from a notification. */
internal const val SHELL_COVER_TAG = "shell_cover"

/** No request to open the timer ever comes. */
private val NO_TIMER_REQUEST: StateFlow<Long> = MutableStateFlow(0L)

/** The two ends of a route transition. */
private class AnimatedTimer(val initial: NavBackStackEntry, val target: NavBackStackEntry)

private fun NavBackStackEntry.isTimer(): Boolean = destination.hasRoute<TimerRoute>()

/** A full-screen route above the shell or the timer: it gives way to a new alert (rule 8). */
private fun NavDestination.isAboveShellAndTimer(): Boolean = !hasRoute<ShellRoute>() && !hasRoute<TimerRoute>()
