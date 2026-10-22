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
import com.umamit.feature.home.HomeScreen
import com.umamit.feature.journal.JournalScreen
import com.umamit.feature.settings.SettingsScreen
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
 */
@Composable
fun UmamitApp(
    activeBrewSource: ActiveBrewSource,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType? = null,
) {
    val controllers = Tab.entries.associateWith { rememberNavController() }
    var selected by rememberSaveable { mutableStateOf(Tab.Start) }
    var segment by rememberSaveable { mutableStateOf(CollectionSegment.MyTeas) }
    val scrollRequests = remember { mutableStateMapOf<Tab, Int>() }
    // Tabs whose top screen was scrolled by a re-select; the next re-select pops them to the root (D-048).
    val scrolledTabs = remember { mutableSetOf<Tab>() }
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
                        if (tab != selected) {
                            selected = tab
                            return@item
                        }
                        // Re-select (03 §2.2, D-048): scroll to the top first, then pop to the root.
                        val controller = controllers.getValue(tab)
                        val deeper = controller.previousBackStackEntry != null
                        if (deeper && tab in scrolledTabs) {
                            scrolledTabs.remove(tab)
                            controller.popBackStack(tab.root, inclusive = false)
                        } else {
                            scrollRequests[tab] = (scrollRequests[tab] ?: 0) + 1
                            if (deeper) scrolledTabs.add(tab)
                        }
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
        Column(modifier = Modifier.fillMaxSize()) {
            transition.AnimatedContent(
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    // Fade-through: the old tab leaves, then the new one arrives (03 §3.6 `tabSwitch`).
                    fadeIn(motion.tabSwitchIn()) togetherWith fadeOut(motion.tabSwitchOut())
                },
            ) { tab ->
                stateHolder.SaveableStateProvider(tab) {
                    TabHost(
                        tab = tab,
                        navController = controllers.getValue(tab),
                        scrollToTopRequest = scrollRequests[tab] ?: 0,
                        segment = segment,
                        onSegmentChange = { segment = it },
                    )
                }
            }
            // Slot above the bar; the timer route (M1-09) will open on click. Without a bar to
            // consume them, the rail layout leaves the system bar insets to the brew bar.
            ActiveBrewBar(
                brew = activeBrew,
                progress = activeBrewSource.progress,
                onClick = {},
                windowInsets = if (resolvedLayout == NavigationSuiteType.NavigationBar) {
                    WindowInsets(0)
                } else {
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End)
                },
            )
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
        composable<StartRoute> { HomeScreen(openSettings, scrollToTopRequest = scrollToTopRequest) }
        composable<JournalRoute> { JournalScreen(openSettings, scrollToTopRequest = scrollToTopRequest) }
        composable<CollectionRoute> {
            CollectionScreen(segment, onSegmentChange, openSettings, scrollToTopRequest = scrollToTopRequest)
        }
        composable<StatsRoute> { StatsScreen(openSettings, scrollToTopRequest = scrollToTopRequest) }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() }, scrollToTopRequest = scrollToTopRequest) {
                // Throwaway probe entry (M1-03a); removed or hidden before v0.1.0.
                OutlinedButton(
                    onClick = { navController.navigate(ProbeRoute) },
                    modifier = Modifier.padding(top = UmamiSpacing.Large),
                ) {
                    Text(stringResource(R.string.probe_entry))
                }
            }
        }
        composable<ProbeRoute> { ProbeScreen(onBack = { navController.popBackStack() }) }
    }
}
