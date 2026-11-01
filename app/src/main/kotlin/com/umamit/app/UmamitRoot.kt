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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.umamit.app.timer.TimerRequest
import com.umamit.core.designsystem.theme.SteepingColorScheme
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TimerSlot
import com.umamit.feature.brew.timer.BrewTimerRoute
import com.umamit.feature.catalog.TeaLinkShare
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
 * (its content or full-screen intent) brings the timer to the top, the timer the notification names (04 §8.2,
 * D-098: a notification opens its own timer, whichever role it has). Until it is there, the root
 * draws the timer's dark background over the shell and reports the timer as visible, so the
 * shell never shows over the lock screen, not even for one frame (D-058). One place decides
 * both ([rememberTimerFirst]).
 *
 * @param journalSources the journal tab and the „Gespeichert" sheet, which never shows over an alert (M1-16).
 * @param onTimerVisibleChange whether the timer is the current route, for the lock-screen flags (SCR-05).
 * @param alertKey the identity of the alert playing now (`TimerUiState.alertKey`), or null.
 * @param timerRequest a new non-zero token for each request to open the timer (a notification's
 *   content or full-screen intent), with the timer it names; token 0 for none.
 * @param onConsumeTimerRequest consumes that token: the timer was opened for it.
 * @param timerScreen the timer route's content; tests replace it, the app uses [BrewTimerRoute].
 *   Its third argument opens the setup of the waiting session (*Brühen anpassen*, BRW-11), its fourth
 *   names the timer the route shows and opens the other one's (D-098).
 * @param startScreen the Start tab's content; the app passes [StartTabContent].
 * @param catalogScreen Sammlung's *Katalog* segment; the app passes [CatalogTabContent].
 * @param catalogScreens a catalog tea's detail (SCR-21) and the *Ratgeber* (SCR-20); tests replace them.
 * @param setupScreen the setup route's content (SCR-04); tests replace it. Its third argument opens a catalog tea's
 *   detail (SCR-21) above it by its key (⋮ *Im Katalog ansehen*, D-098), shown with [catalogScreens].
 * @param onboardingCompleted whether the first-start setup is done (ONB-04); null until the settings are
 *   read, and nothing is shown until then. Not done: the setup (SCR-01) is the start destination.
 * @param onboardingScreen the setup's content; tests replace it, the app uses [OnboardingScreenRoute].
 * @param settings the settings, *Über* and *Diagnose* (SCR-25, SCR-27), in the tabs and above the timer;
 *   tests replace them.
 * @param quickTimerScreen the quick timer route's content (SCR-07); tests replace it. Its second argument
 *   opens the timer once the quick timer runs.
 * @param teaLinkShare a link shared to the app and not taken yet (CAT-17); the shell takes it to
 *   Sammlung › Katalog, which loads it and hands its token to [onTakeTeaLinkShare]. Nothing above
 *   the tabs is closed for it: while the timer or another full-screen route is shown, it waits (rule 8).
 * @param catalogRefreshRequest a new non-zero token for each tap on a catalog refresh notification
 *   (SCR-22, D-097); 0 for none. The root opens Sammlung › Katalog with the refresh sheet above what
 *   is shown ([CatalogRefreshRoute], at most one, replacing an older one), so back returns there;
 *   during an alert it waits until the alert has ended (rule 8). Before the first-start setup is done,
 *   or while a setup is shown, the tap opens the app where it was (ONB-03). Either way
 *   [onConsumeCatalogRefreshRequest] gets the token.
 * @param startRequest a new non-zero token for each tap on the daily low-stock reminder (INV-11, D-098); 0 for
 *   none. The root closes everything above the shell (the timer keeps running) and shows Start at its root;
 *   during an alert it waits until the alert has ended (rule 8). Before the first-start setup is done, or while
 *   a setup is shown, the tap is let go (ONB-03). Either way [onConsumeStartRequest] gets the token.
 */
@Composable
internal fun UmamitRoot(
    activeBrewSource: ActiveBrewSource,
    journalSources: JournalSources,
    alertKey: StateFlow<String?>,
    modifier: Modifier = Modifier,
    timerRequest: StateFlow<TimerRequest> = NO_TIMER_REQUEST,
    onConsumeTimerRequest: (Long) -> Unit = {},
    onTimerVisibleChange: (Boolean) -> Unit = {},
    timerScreen: @Composable (
        onClose: () -> Unit,
        onOpenTimerSettings: () -> Unit,
        onAdjustBrew: (sessionId: String) -> Unit,
        shown: TimerShown,
    ) -> Unit = { onClose, onSettings, onAdjust, shown ->
        BrewTimerRoute(
            onClose = onClose,
            onAdjustBrew = onAdjust,
            onOpenTimerSettings = onSettings,
            slot = shown.slot,
            onPin = shown.onPin,
            onShowOther = shown.onShowOther,
        )
    },
    startScreen: @Composable (StartTab) -> Unit = { PlaceholderStart(it) },
    catalogScreen: @Composable (CatalogTab) -> Unit = { PlaceholderCatalog(it) },
    catalogScreens: CatalogStackScreens = CatalogStackScreens(),
    setupScreen: @Composable (
        onBack: () -> Unit,
        onOpenTimer: () -> Unit,
        onOpenCatalog: (key: String) -> Unit,
    ) -> Unit = { onBack, onOpenTimer, onOpenCatalog ->
        BrewSetupScreenRoute(onBack = onBack, onOpenTimer = onOpenTimer, onOpenCatalog = onOpenCatalog)
    },
    onboardingCompleted: StateFlow<Boolean?> = MutableStateFlow(true),
    onboardingScreen: @Composable (onFinish: () -> Unit) -> Unit = { onFinish ->
        OnboardingScreenRoute(onFinish = onFinish)
    },
    settings: SettingsScreens = SettingsScreens(),
    quickTimerScreen: @Composable (onBack: () -> Unit, onOpenTimer: () -> Unit) -> Unit = { onBack, onOpenTimer ->
        QuickTimerScreenRoute(onBack = onBack, onOpenTimer = onOpenTimer)
    },
    teaLinkShare: StateFlow<TeaLinkShare?> = NO_TEA_LINK_SHARE,
    onTakeTeaLinkShare: (Long) -> Unit = {},
    catalogRefreshRequest: StateFlow<Long> = NO_CATALOG_REFRESH_REQUEST,
    onConsumeCatalogRefreshRequest: (Long) -> Unit = {},
    startRequest: StateFlow<Long> = NO_START_REQUEST,
    onConsumeStartRequest: (Long) -> Unit = {},
) {
    val navController = rememberNavController()
    val share by teaLinkShare.collectAsStateWithLifecycle()
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
    CatalogRefreshTaps(
        navController = navController,
        alert = alert,
        setupDone = completed == true,
        request = catalogRefreshRequest,
        onConsume = onConsumeCatalogRefreshRequest,
        navigateAbove = timerFirst.navigateAbove,
    )
    val showStart = startTaps(
        navController = navController,
        alert = alert,
        setupDone = completed == true,
        request = startRequest,
        onConsume = onConsumeStartRequest,
    )

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
    val closeTimer: () -> Unit = { navController.popBackStack<TimerRoute>(inclusive = true) }
    val openTimer: () -> Unit = { navController.navigate(TimerRoute()) { launchSingleTop = true } }
    // A half of the bar with two timers (D-098).
    val openTimerOf: (
        TimerSlot,
    ) -> Unit = { slot -> navController.navigate(TimerRoute(slot)) { launchSingleTop = true } }
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
        openTimerOf = openTimerOf,
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
                    onOpenTimerOf = openTimerOf,
                    startNavigation = startNavigation,
                    alertKey = alert,
                    startScreen = startScreen,
                    catalogScreen = catalogScreen,
                    catalogScreens = catalogScreens,
                    settings = settings.withRerunSetup(rerunSetup),
                    teaLinkShare = share,
                    onTakeTeaLinkShare = onTakeTeaLinkShare,
                    startRequest = showStart,
                )
            }
            // A shell of its own above what was shown (D-097): back leaves it, its tabs work as the shell's.
            composable<CatalogRefreshRoute> { entry ->
                // The token this entry shows, never a later tap's (#121).
                val sheetToken = entry.toRoute<CatalogRefreshRoute>().sheetToken
                UmamitApp(
                    activeBrewSource = activeBrewSource,
                    journalSources = journalSources,
                    alerting = alert != null,
                    onOpenTimer = openTimer,
                    onOpenTimerOf = openTimerOf,
                    startNavigation = startNavigation,
                    alertKey = alert,
                    startScreen = startScreen,
                    catalogScreen = catalogScreen,
                    catalogScreens = catalogScreens,
                    settings = settings.withRerunSetup(rerunSetup),
                    teaLinkShare = share,
                    onTakeTeaLinkShare = onTakeTeaLinkShare,
                    opening = ShellOpening.CatalogRefresh(sheetToken),
                )
            }
            composable<OnboardingRoute> { onboardingScreen(finishSetup) }
            composable<TimerRoute> { timerEntry ->
                val route = timerEntry.toRoute<TimerRoute>()
                timerScreen(
                    closeTimer,
                    { navigateAbove(TimerSettingsRoute) {} },
                    { sessionId ->
                        // The setup replaces the timer; *Brühen starten* there opens it again (BRW-11).
                        navigateAbove(BrewSetupRoute(sessionId = sessionId)) {
                            popUpTo<TimerRoute> { inclusive = true }
                        }
                    },
                    TimerShown(
                        slot = route.slot,
                        // The timer a route opened for none shows, for a later notification's request (04 §8.2).
                        onPin = { pinned -> timerEntry.pinTimer(pinned) },
                        onShowOther = { other -> navController.showOtherTimer(timerEntry, route, other) },
                    ),
                )
            }
            composable<BrewSetupRoute> {
                setupScreen(
                    // Only while resumed: a quick double tap never pops the shell under it (D-090).
                    dropUnlessResumed { navController.popBackStack() },
                    // The timer replaces the setup, so back from the timer returns to where the brew began.
                    {
                        navController.navigate(TimerRoute(TimerSlot.BREW)) {
                            popUpTo<BrewSetupRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    // ⋮ *Im Katalog ansehen* (D-098): the detail above the setup, which keeps its draft in its entry.
                    // During an alert the alerting timer instead, never a page over it (rule 8, D-062 S3).
                    { key ->
                        unlessAlerting({ currentAlerting }, instead = openTimer) {
                            navigateAbove(SetupCatalogTeaRoute(key)) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable<SetupCatalogTeaRoute> {
                catalogScreens.tea(
                    // The tea is in the collection already: a saved purchase stays on the detail (no brew flow here).
                    CatalogTeaHost(
                        onBack = dropUnlessResumed { navController.popBackStack() },
                        alerting = alert != null,
                        onPurchaseSave = {},
                    ),
                )
            }
            composable<QuickTimerRoute> {
                quickTimerScreen(
                    dropUnlessResumed { navController.popBackStack() },
                    // The timer replaces it, so back from the running quick timer returns to Start.
                    {
                        navController.navigate(TimerRoute(TimerSlot.QUICK)) {
                            popUpTo<QuickTimerRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<TimerSettingsRoute> {
                settings.screen(
                    SettingsHost(
                        onBack = dropUnlessResumed { navController.popBackStack() },
                        onOpenAbout = { navigateAbove(AboutRoute) {} },
                        alertKey = alert,
                        onOpenTeaware = dropUnlessResumed { navigateAbove(TeawareRoute) {} },
                    ),
                )
            }
            // Teegeschirr above the timer (from *Timer-Einstellungen*), and the vessel editor above it or above
            // the setup (*Neues Gefäß …*, WARE-02); both give way to a new alert like every route above the
            // shell (rule 8).
            composable<TeawareRoute> { entry ->
                settings.teaware(
                    TeawareHost(
                        onBack = dropUnlessResumed { navController.popBackStack() },
                        onOpenVessel = { id -> navigateAbove(VesselEditorRoute(id)) { launchSingleTop = true } },
                        onAddVessel = dropUnlessResumed {
                            navigateAbove(VesselEditorRoute()) { launchSingleTop = true }
                        },
                        archived = entry.archivedVessel(),
                        onArchivedSnackbarEnd = entry::forgetArchivedVessel,
                    ),
                )
            }
            composable<VesselEditorRoute> {
                settings.vesselEditor(
                    VesselEditorHost(
                        onClose = dropUnlessResumed { navController.popBackStack() },
                        onSave = { id -> navController.closeVesselEditor(savedVesselId = id) },
                        onArchive = { archived -> navController.closeVesselEditor(archived = archived) },
                    ),
                )
            }
            composable<AboutRoute> {
                settings.about(
                    AboutHost(
                        onBack = dropUnlessResumed { navController.popBackStack() },
                        onRerunSetup = rerunSetup,
                        onOpenDiagnostics = { navigateAbove(DiagnosticsRoute) {} },
                    ),
                )
            }
            composable<DiagnosticsRoute> { settings.diagnostics(dropUnlessResumed { navController.popBackStack() }) }
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

/**
 * The taps on a catalog refresh notification (SCR-22, 03 §2.4, D-097), each once: with the setup
 * done and no setup on screen, Sammlung › Katalog opens with the refresh sheet above what is shown
 * ([CatalogRefreshRoute]); during an alert the tap waits until the alert has ended, so neither
 * covers the timer (rule 8). Before the setup is done the tap is let go: the app opens where it
 * was (ONB-03).
 *
 * There is at most one refresh shell, and it shows its own tap's token (#121): on top already, it
 * takes the new token in place; lower in the back stack, it leaves with what was opened above it,
 * and the new one opens above what was below it. A timer opened from the older shell stays, below
 * the new one, so back returns to it.
 */
@Composable
@Suppress("LongParameterList") // the navigation, the two gates, the request and its consumer
private fun CatalogRefreshTaps(
    navController: NavHostController,
    alert: String?,
    setupDone: Boolean,
    request: StateFlow<Long>,
    onConsume: (Long) -> Unit,
    navigateAbove: (route: Any, options: NavOptionsBuilder.() -> Unit) -> Unit,
) {
    var handled by rememberSaveable { mutableLongStateOf(0L) }
    val token by request.collectAsStateWithLifecycle()
    val currentOnConsume by rememberUpdatedState(onConsume)
    val currentNavigateAbove by rememberUpdatedState(navigateAbove)
    LaunchedEffect(token, alert, setupDone) {
        if (token == 0L || token == handled) return@LaunchedEffect
        val inSetup = navController.currentDestination?.hasRoute<OnboardingRoute>() == true
        if (setupDone && !inSetup) {
            if (alert != null) return@LaunchedEffect
            navController.openCatalogRefresh(CatalogRefreshRoute(token), currentNavigateAbove)
        }
        handled = token
        currentOnConsume(token)
    }
}

/**
 * The taps on the daily low-stock reminder (INV-11, 03 §2.4, D-098), each once: with the setup done and no
 * setup on screen, everything above the app's shell closes (a timer keeps running behind the active brew bar)
 * and the shell shows Start at its root, for the token returned; during an alert the tap waits until the
 * alert has ended (rule 8). Before the setup is done the tap is let go (ONB-03).
 *
 * @return the token of the last tap shown, for the shell; 0 before the first.
 */
@Composable
private fun startTaps(
    navController: NavHostController,
    alert: String?,
    setupDone: Boolean,
    request: StateFlow<Long>,
    onConsume: (Long) -> Unit,
): Long {
    var handled by rememberSaveable { mutableLongStateOf(0L) }
    var shown by rememberSaveable { mutableLongStateOf(0L) }
    val token by request.collectAsStateWithLifecycle()
    val currentOnConsume by rememberUpdatedState(onConsume)
    LaunchedEffect(token, alert, setupDone) {
        if (token == 0L || token == handled) return@LaunchedEffect
        val inSetup = navController.currentDestination?.hasRoute<OnboardingRoute>() == true
        if (setupDone && !inSetup) {
            if (alert != null) return@LaunchedEffect
            navController.popBackStack<ShellRoute>(inclusive = false)
            shown = token
        }
        handled = token
        currentOnConsume(token)
    }
    return shown
}

/** Opens [route], replacing the refresh shell on the back stack, if any (see [CatalogRefreshTaps]). */
private fun NavHostController.openCatalogRefresh(
    route: CatalogRefreshRoute,
    navigateAbove: (route: Any, options: NavOptionsBuilder.() -> Unit) -> Unit,
) {
    if (currentDestination?.hasRoute<CatalogRefreshRoute>() == true) {
        // The same entry with the new token: its shell turns to the catalog and shows the sheet.
        navigate(route) { launchSingleTop = true }
        return
    }
    val timerBefore = hasTimer()
    // An older refresh shell leaves with what was opened above it; a timer among that comes back.
    val replaced = popBackStack<CatalogRefreshRoute>(inclusive = true)
    if (replaced && timerBefore && !hasTimer()) navigate(TimerRoute()) { launchSingleTop = true }
    navigateAbove(route) { launchSingleTop = true }
}

/** Runs [open], or [instead] while an alert plays ([alerting]): nothing opens over an alert (rule 8). */
private inline fun unlessAlerting(alerting: () -> Boolean, instead: () -> Unit, open: () -> Unit) {
    if (alerting()) instead() else open()
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
 * - **a notification's request** (a new [timerRequest] token): the timer it names comes to the top, and
 *   the activity forgets the token ([onConsume]) only then, so a recreation before keeps it.
 *
 * Either way the timer is popped back to when it is on the stack ([popBackToTimerOf]), else it replaces what lies
 * above the shell, without a transition while alerting. The alert a route was opened in and the
 * handled token survive a configuration change.
 */
@Composable
private fun rememberTimerFirst(
    navController: NavHostController,
    alert: String?,
    timerRequest: StateFlow<TimerRequest>,
    onConsume: (Long) -> Unit,
): TimerFirst {
    // The alert the route on top of the shell or timer was opened in (null: none played). Only that alert
    // may stay under it.
    var aboveAlert by rememberSaveable { mutableStateOf<String?>(null) }
    var handledRequest by rememberSaveable { mutableLongStateOf(0L) }
    val request by timerRequest.collectAsStateWithLifecycle()
    val currentAlert by rememberUpdatedState(alert)
    val currentOnConsume by rememberUpdatedState(onConsume)

    val bringTimerToTop = { slot: TimerSlot? ->
        if (!navController.popBackToTimerOf(slot)) {
            navController.navigate(TimerRoute(slot)) {
                popUpTo<ShellRoute> { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    val giveWayToNewAlert = {
        val playing = currentAlert
        val above = navController.currentDestination?.isAboveShellAndTimer() == true
        if (playing != null && above && playing != aboveAlert) bringTimerToTop(null)
    }
    LaunchedEffect(alert) { giveWayToNewAlert() }
    // A defensive belt: the keyed effect above already sees a new alert after the background, because the
    // collected key resumes with it. This also checks once on every resume, should that ever change.
    LifecycleResumeEffect(Unit) {
        giveWayToNewAlert()
        onPauseOrDispose {}
    }
    LaunchedEffect(request) {
        val token = request.token
        if (token != 0L && token != handledRequest) {
            bringTimerToTop(request.slot)
            handledRequest = token
            currentOnConsume(token)
        }
    }
    return TimerFirst(
        requestPending = request.token != 0L && request.token != handledRequest,
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

/**
 * What the timer route's content gets about its timer (D-098): the [slot] it shows (null: the one in front when it
 * opened), [onPin], which a route opened for none calls with the timer it then keeps showing, and
 * [onShowOther], a tap on the other timer's row.
 */
internal class TimerShown(
    val slot: TimerSlot?,
    val onPin: (TimerSlot) -> Unit = {},
    val onShowOther: (TimerSlot) -> Unit = {},
)

/** The cover over the shell while the timer opens from a notification. */
internal const val SHELL_COVER_TAG = "shell_cover"

/** No request to open the timer ever comes. */
private val NO_TIMER_REQUEST: StateFlow<TimerRequest> = MutableStateFlow(TimerRequest(0L))

/** No catalog refresh notification is ever tapped. */
private val NO_CATALOG_REFRESH_REQUEST: StateFlow<Long> = MutableStateFlow(0L)

/** The daily low-stock reminder is never tapped. */
private val NO_START_REQUEST: StateFlow<Long> = MutableStateFlow(0L)

/** No link is ever shared. */
private val NO_TEA_LINK_SHARE: StateFlow<TeaLinkShare?> = MutableStateFlow(null)

/** The two ends of a route transition. */
private class AnimatedTimer(val initial: NavBackStackEntry, val target: NavBackStackEntry)

private fun NavBackStackEntry.isTimer(): Boolean = destination.hasRoute<TimerRoute>()

/** A full-screen route above the shell or the timer: it gives way to a new alert (rule 8). */
private fun NavDestination.isAboveShellAndTimer(): Boolean = !hasRoute<ShellRoute>() && !hasRoute<TimerRoute>()
