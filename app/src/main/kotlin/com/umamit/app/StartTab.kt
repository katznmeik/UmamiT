package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.model.TimerSlot
import com.umamit.core.ui.SheetHandOver
import com.umamit.core.ui.SheetStandIn
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.brew.picker.TeaPickerHost
import com.umamit.feature.brew.picker.TeaPickerViewModel
import com.umamit.feature.home.HomeActions
import com.umamit.feature.home.HomeRoute
import com.umamit.feature.home.HomeScreen
import com.umamit.feature.home.HomeUiState
import com.umamit.feature.home.HomeViewModel

/**
 * Where Start leads outside its tab (03 §2.1): the setup, the timer (the one in front, or [openTimerOf] a slot's)
 * and the quick timer.
 */
internal class StartNavigation(
    val openSetup: (teaId: String) -> Unit = {},
    /** The setup of a tea with one of its recipes (null: the default), from the tea detail (SCR-09). */
    val openSetupWith: (teaId: String, recipeId: String?) -> Unit = { teaId, _ -> openSetup(teaId) },
    val openTimer: () -> Unit = {},
    val openQuickTimer: () -> Unit = {},
    val openTimerOf: (TimerSlot) -> Unit = { openTimer() },
    /** *So nochmal aufgießen* on a session's page (SCR-16, BRW-06): the setup prefilled with that past session. */
    val openSetupFromSession: (sessionId: String) -> Unit = {},
    /**
     * Whether a route above the shell covers it now, from the back stack (it changes when navigation starts): the
     * picker's frozen frame stays below that route only while it does (D-129).
     */
    val shellCovered: () -> Boolean = { false },
)

/**
 * What the shell hands the Start tab: its own actions and the root's [navigation].
 *
 * @property alertKey the alert playing now, or null: the tea picker never covers it (rule 8).
 * @property openCatalogForBrew the tea picker's *Aus dem Katalog*: Sammlung › Katalog, whose add
 *   or repurchase then opens the setup of that tea (D-073, D-088).
 * @property pickerReturn the picker flow was left without a save: Start opens the picker again,
 *   unless an alert plays ([onPickerReturned] confirms it was seen).
 * @property brewBarHold where Start tells the shell that it hands off to the timer (D-111).
 * @property pickerHandOver the picker's hand-over to the setup, whose stand-in the shell draws (D-129); null: none.
 */
internal class StartTab(
    val openSettings: () -> Unit,
    val scrollToTopRequest: Int,
    val showJournal: () -> Unit,
    val openCatalog: () -> Unit,
    val alertKey: String?,
    val navigation: StartNavigation,
    val openCatalogForBrew: () -> Unit = {},
    val pickerReturn: Boolean = false,
    val onPickerReturned: () -> Unit = {},
    val brewBarHold: BrewBarHold = BrewBarHold(),
    val pickerHandOver: SheetHandOver? = null,
)

/**
 * Start's hand-off to the timer, for the shell (D-111): while Start holds still after *Nochmal aufgießen*, the active
 * brew bar does not grow in. The Start tab sets [source] to its view model's snapshot state, so the shell reads it
 * in the frame the new brew arrives.
 */
internal class BrewBarHold {
    var source: State<Boolean>? by mutableStateOf(null)

    /** Start holds still now. */
    val held: Boolean get() = source?.value == true
}

/**
 * The bar's brew while Start hands off to the timer (D-111): [held], a bar that was not shown stays away, so it does
 * not grow in under the timer's fade; one already shown stays live. Released, it is [brew] again.
 */
@Composable
internal fun heldBar(brew: ActiveBrew?, held: Boolean): ActiveBrew? {
    val shown = remember { BarShown(brew != null) }
    SideEffect { if (!held) shown.value = brew != null }
    return brew.takeIf { !held || shown.value }
}

/** Whether the bar was shown in the last frame without a hold. */
private class BarShown(var value: Boolean)

/**
 * Start with its view model and the tea picker (SCR-02, SCR-03): the app's Start tab. Tests pass
 * view models built on fakes, so the picker's catalog flow is tested through this wiring (#72).
 */
@Suppress("ViewModelForwarding") // the two screens' own view models, replaceable only for tests
@Composable
internal fun StartTabContent(
    tab: StartTab,
    homeViewModel: HomeViewModel = hiltViewModel(),
    pickerViewModel: TeaPickerViewModel = hiltViewModel(),
) {
    val picker = rememberAlertGate(tab.alertKey)
    val navigation = tab.navigation
    // Back from the picker's catalog flow (D-088): the picker again, never over an alert (rule 8).
    val currentOnPickerReturned by rememberUpdatedState(tab.onPickerReturned)
    LaunchedEffect(tab.pickerReturn) {
        if (tab.pickerReturn) {
            currentOnPickerReturned()
            if (tab.alertKey == null) picker.open()
        }
    }
    // An alert ends a hand-over: the sheet and its stand-in are already gone in its first frame (rule 8, D-088).
    val handOver = tab.pickerHandOver
    LaunchedEffect(tab.alertKey, handOver) {
        if (tab.alertKey != null) handOver?.clear()
    }
    val hold = tab.brewBarHold
    DisposableEffect(hold, homeViewModel) {
        hold.source = homeViewModel.handOff.handingOff
        onDispose { hold.source = null }
    }
    HomeRoute(
        actions = HomeActions(
            openSettings = tab.openSettings,
            startBrew = picker::open,
            // *Aufguss fortsetzen* shows a brew (SCR-02, D-098, D-108): it opens that brew, not the timer in front.
            openTimer = navigation.openTimerOf,
            quickTimer = navigation.openQuickTimer,
            openSetup = navigation.openSetup,
            showJournal = tab.showJournal,
            openCatalog = tab.openCatalog,
        ),
        onOpenTimer = { slot -> slot?.let(navigation.openTimerOf) ?: navigation.openTimer() },
        scrollToTopRequest = tab.scrollToTopRequest,
        viewModel = homeViewModel,
    )
    TeaPickerHost(
        gate = picker,
        // The setup opens in the tap's frame (D-062). The sheet stays until its frozen frame stands in for it below the
        // setup, which fades in over that frame, so Start never shows between them (D-129); during an alert there is
        // no hand-over, and the alerting timer opens instead.
        onPickTea = {
            picker.close()
            navigation.openSetup(it)
        },
        onQuickTimer = {
            picker.close()
            navigation.openQuickTimer()
        },
        onDismissRequest = picker::close,
        onCatalog = {
            picker.close()
            tab.openCatalogForBrew()
        },
        handOver = handOver.takeIf { tab.alertKey == null },
        viewModel = pickerViewModel,
    )
}

/**
 * The picker's frozen frame (D-129) while [handOver] runs, below a route opened above the shell ([covered]); never
 * during an alert (rule 8), so it is gone in the alert's first frame, as the sheet is.
 */
@Composable
internal fun PickerStandIn(handOver: SheetHandOver, alertKey: String?, covered: () -> Boolean) {
    if (alertKey == null) SheetStandIn(handOver, covered)
}

/** A route above a shell (no route yet: false): the picker's frozen frame stays below it (D-129). */
internal fun NavBackStackEntry?.coversShell(): Boolean =
    this != null && !destination.hasRoute<ShellRoute>() && !destination.hasRoute<CatalogRefreshRoute>()

/** A calm Start without data, for tests and previews of the shell: the empty state. */
@Composable
internal fun PlaceholderStart(tab: StartTab) {
    HomeScreen(
        state = HomeUiState(loading = false, collectionEmpty = true),
        nowElapsedMs = { 0L },
        actions = HomeActions(openSettings = tab.openSettings, openCatalog = tab.openCatalog),
        scrollToTopRequest = tab.scrollToTopRequest,
    )
}
