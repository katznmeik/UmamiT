package com.umamit.app

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.umamit.app.probe.ProbeScreen
import com.umamit.core.designsystem.component.ActiveBrewBar
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.collection.CollectionScreen
import com.umamit.feature.collection.CollectionSegment
import com.umamit.feature.journal.JournalScreen
import com.umamit.feature.settings.SettingsScreen
import com.umamit.feature.settings.diagnostics.DiagnosticsRoute
import com.umamit.feature.stats.StatsScreen
import kotlin.coroutines.cancellation.CancellationException

internal fun tabTag(tab: Tab) = "tab_${tab.name}"

/** Divides the tab slide: a quiet nudge of a twelfth of the width, no travel across the screen. */
private const val SLIDE_FRACTION = 12

/**
 * The navigation shell (03 §2, 06 §5.5): four tabs in a bottom bar on phones and
 * a rail on wide windows, the active brew bar slot above the bar, and one
 * navigation controller per tab, so every tab keeps its own back stack.
 *
 * @param layoutType forces the bar or the rail; `null` follows the window size.
 * @param onOpenTimer a tap on the active brew bar opens the timer (GEN-06).
 * @param startNavigation where Start leads outside the shell (setup, timer, quick timer).
 * @param alertKey the alert playing now, or null (rule 8).
 * @param startScreen the Start tab's content; the app uses [StartTabContent], tests the calm placeholder.
 */
@Composable
internal fun UmamitApp(
    activeBrewSource: ActiveBrewSource,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType? = null,
    onOpenTimer: () -> Unit = {},
    startNavigation: StartNavigation = StartNavigation(),
    alertKey: String? = null,
    startScreen: @Composable (StartTab) -> Unit = { PlaceholderStart(it) },
) {
    val controllers = Tab.entries.associateWith { rememberNavController() }
    var selected by rememberSaveable { mutableStateOf(Tab.Start) }
    var segment by rememberSaveable { mutableStateOf(CollectionSegment.MyTeas) }
    val reselection = remember { TabReselection() }
    val activeBrew by activeBrewSource.activeBrew.collectAsStateWithLifecycle()
    val resolvedLayout = layoutType
        ?: NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val motion = UmamiTheme.motion

    // Tab state (scroll positions, rememberSaveable) outlives a tab leaving the composition.
    val stateHolder = rememberSaveableStateHolder()
    // Back leaves a tab's own stack first (its NavHost registers later and wins), then returns to Start,
    // with a predictive-back preview of the tab transition.
    val transitionState = remember { SeekableTransitionState(selected) }
    val transition = rememberTransition(transitionState, label = "tab")
    LaunchedEffect(selected) {
        if (transitionState.currentState != selected || transitionState.targetState != selected) {
            transitionState.animateTo(selected)
        }
    }
    PredictiveBackHandler(enabled = selected != Tab.Start) { progress ->
        try {
            progress.collect { transitionState.seekTo(it.progress, Tab.Start) }
            selected = Tab.Start
        } catch (e: CancellationException) {
            transitionState.snapTo(selected)
            throw e
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = tab == selected,
                    onClick = {
                        if (tab != selected) selected = tab else reselection.reselect(tab, controllers.getValue(tab))
                    },
                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                    modifier = Modifier.testTag(tabTag(tab)),
                )
            }
        },
        modifier = modifier,
        layoutType = resolvedLayout,
    ) {
        val rail = resolvedLayout != NavigationSuiteType.NavigationBar
        // In the rail layout the brew bar applies the bottom inset below the content, so the content must not.
        val insetBelowContent = if (rail && activeBrew != null) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
        } else {
            WindowInsets(0)
        }
        Column(modifier = Modifier.fillMaxSize()) {
            transition.AnimatedContent(
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(insetBelowContent),
                transitionSpec = {
                    // Fade-through: the old tab leaves, then the new one arrives (03 §3.6 `tabSwitch`).
                    fadeIn(motion.tabSwitchIn()) togetherWith fadeOut(motion.tabSwitchOut())
                },
            ) { tab ->
                stateHolder.SaveableStateProvider(tab) {
                    TabHost(
                        tab = tab,
                        navController = controllers.getValue(tab),
                        scrollToTopRequest = reselection.scrollRequest(tab),
                        segment = segment,
                        onSegmentChange = { segment = it },
                        start = { openSettings, scrollToTop ->
                            startScreen(
                                StartTab(
                                    openSettings = openSettings,
                                    scrollToTopRequest = scrollToTop,
                                    showJournal = { selected = Tab.Journal },
                                    openCatalog = {
                                        segment = CollectionSegment.Catalog
                                        selected = Tab.Collection
                                    },
                                    alertKey = alertKey,
                                    navigation = startNavigation,
                                ),
                            )
                        },
                    )
                }
            }
            // Slot above the bar; a tap opens the timer. Without a bar to consume them, the rail
            // layout leaves the system bar insets to the brew bar.
            ActiveBrewBar(
                brew = activeBrew,
                progress = activeBrewSource.progress,
                onClick = onOpenTimer,
                windowInsets = if (!rail) {
                    WindowInsets(0)
                } else {
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End)
                },
            )
        }
    }
}

/**
 * Re-selecting a tab (03 §2.2, D-049): the first tap scrolls the screen on top to
 * the top, the next tap on that same screen pops a deeper stack to the root.
 */
private class TabReselection {
    private val scrollRequests = mutableStateMapOf<Tab, Int>()

    // Per tab, the back stack entry a re-select scrolled. Keyed by the entry, so a screen
    // pushed again later scrolls first instead of popping.
    private val scrolledEntries = mutableMapOf<Tab, String>()

    /** A counter per tab; every increase scrolls the screen on top (a one-shot, never replayed). */
    fun scrollRequest(tab: Tab): Int = scrollRequests[tab] ?: 0

    fun reselect(tab: Tab, controller: NavHostController) {
        val top = controller.currentBackStackEntry?.id
        val deeper = controller.previousBackStackEntry != null
        if (deeper && top != null && scrolledEntries[tab] == top) {
            scrolledEntries.remove(tab)
            controller.popBackStack(tab.root, inclusive = false)
        } else {
            scrollRequests[tab] = scrollRequest(tab) + 1
            if (deeper && top != null) scrolledEntries[tab] = top else scrolledEntries.remove(tab)
        }
    }
}

@Composable
private fun TabHost(
    tab: Tab,
    navController: NavHostController,
    scrollToTopRequest: Int,
    segment: CollectionSegment,
    onSegmentChange: (CollectionSegment) -> Unit,
    start: @Composable (openSettings: () -> Unit, scrollToTopRequest: Int) -> Unit,
) {
    val motion = UmamiTheme.motion
    val openSettings = { navController.navigate(SettingsRoute) }
    NavHost(
        navController = navController,
        startDestination = tab.root,
        // Calm routes: a fade with a faint nudge, no bounce (03 §3.6).
        enterTransition = {
            fadeIn(motion.fade()) + slideInHorizontally(motion.standard()) { it / SLIDE_FRACTION }
        },
        // The screen underneath stays solid; only the top one fades and nudges.
        exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            fadeOut(motion.exitFade()) + slideOutHorizontally(motion.exit()) { it / SLIDE_FRACTION }
        },
    ) {
        composable<StartRoute> { start(openSettings, scrollToTopRequest) }
        composable<JournalRoute> { JournalScreen(openSettings, scrollToTopRequest = scrollToTopRequest) }
        composable<CollectionRoute> {
            CollectionScreen(segment, onSegmentChange, openSettings, scrollToTopRequest = scrollToTopRequest)
        }
        composable<StatsRoute> { StatsScreen(openSettings, scrollToTopRequest = scrollToTopRequest) }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = { navController.navigate(DiagnosticsRoute) },
                scrollToTopRequest = scrollToTopRequest,
            ) {
                // Throwaway probe entry (M1-03a); removed or hidden before v0.1.0.
                OutlinedButton(
                    onClick = { navController.navigate(ProbeRoute) },
                    modifier = Modifier.padding(top = UmamiSpacing.Large),
                ) {
                    Text(stringResource(R.string.probe_entry))
                }
            }
        }
        composable<DiagnosticsRoute> { DiagnosticsRoute(onBack = { navController.popBackStack() }) }
        composable<ProbeRoute> { ProbeScreen(onBack = { navController.popBackStack() }) }
    }
}
