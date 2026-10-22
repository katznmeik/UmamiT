package com.umamit.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    val activeBrew by activeBrewSource.activeBrew.collectAsStateWithLifecycle()

    // Back leaves a tab's own stack first (the tab's NavHost registers later and wins), then returns to Start.
    BackHandler(enabled = selected != Tab.Start) { selected = Tab.Start }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = tab == selected,
                    onClick = {
                        if (tab == selected) {
                            // Re-select: scroll to the top, then pop to the root (03 §2.2).
                            scrollRequests[tab] = (scrollRequests[tab] ?: 0) + 1
                            controllers.getValue(tab).popBackStack(tab.root, inclusive = false)
                        } else {
                            selected = tab
                        }
                    },
                    icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                    modifier = Modifier.testTag(tabTag(tab)),
                )
            }
        },
        modifier = modifier,
        layoutType = layoutType
            ?: NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = selected,
                modifier = Modifier.weight(1f),
                animationSpec = UmamiTheme.motion.fade(),
                label = "tab",
            ) { tab ->
                TabHost(
                    tab = tab,
                    navController = controllers.getValue(tab),
                    scrollToTopRequest = scrollRequests[tab] ?: 0,
                    segment = segment,
                    onSegmentChange = { segment = it },
                )
            }
            // Slot above the bar; the timer route (M1-09) will open on click.
            ActiveBrewBar(brew = activeBrew, progress = activeBrewSource.progress, onClick = {})
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
            fadeIn(motion.standard()) + slideInHorizontally(motion.standard()) { it / SLIDE_FRACTION }
        },
        exitTransition = { fadeOut(motion.exit()) },
        popEnterTransition = { fadeIn(motion.standard()) },
        popExitTransition = {
            fadeOut(motion.exit()) + slideOutHorizontally(motion.exit()) { it / SLIDE_FRACTION }
        },
    ) {
        composable<StartRoute> { HomeScreen(openSettings, scrollToTopRequest = scrollToTopRequest) }
        composable<JournalRoute> { JournalScreen(openSettings, scrollToTopRequest = scrollToTopRequest) }
        composable<CollectionRoute> {
            CollectionScreen(segment, onSegmentChange, openSettings, scrollToTopRequest = scrollToTopRequest)
        }
        composable<StatsRoute> { StatsScreen(openSettings, scrollToTopRequest = scrollToTopRequest) }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() }) {
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
