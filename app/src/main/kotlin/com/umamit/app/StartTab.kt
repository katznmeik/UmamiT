package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.umamit.core.model.TimerSlot
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
)

/**
 * What the shell hands the Start tab: its own actions and the root's [navigation].
 *
 * @property alertKey the alert playing now, or null: the tea picker never covers it (rule 8).
 * @property openCatalogForBrew the tea picker's *Aus dem Katalog*: Sammlung › Katalog, whose add
 *   or repurchase then opens the setup of that tea (D-073, D-088).
 * @property pickerReturn the picker flow was left without a save: Start opens the picker again,
 *   unless an alert plays ([onPickerReturned] confirms it was seen).
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
)

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
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpenedIn by rememberSaveable { mutableStateOf<String?>(null) }
    val navigation = tab.navigation
    // Back from the picker's catalog flow (D-088): the picker again, never over an alert (rule 8).
    val currentOnPickerReturned by rememberUpdatedState(tab.onPickerReturned)
    LaunchedEffect(tab.pickerReturn) {
        if (tab.pickerReturn) {
            currentOnPickerReturned()
            if (tab.alertKey == null) {
                pickerOpenedIn = null
                pickerOpen = true
            }
        }
    }
    HomeRoute(
        actions = HomeActions(
            openSettings = tab.openSettings,
            startBrew = {
                pickerOpenedIn = tab.alertKey
                pickerOpen = true
            },
            // *Aufguss fortsetzen* shows the brew (SCR-02, D-098), so it opens the brew, never the timer in front.
            openTimer = { navigation.openTimerOf(TimerSlot.BREW) },
            quickTimer = navigation.openQuickTimer,
            openSetup = navigation.openSetup,
            showJournal = tab.showJournal,
            openCatalog = tab.openCatalog,
        ),
        onOpenTimer = navigation.openTimer,
        scrollToTopRequest = tab.scrollToTopRequest,
        viewModel = homeViewModel,
    )
    TeaPickerHost(
        open = pickerOpen,
        openedIn = pickerOpenedIn,
        alertKey = tab.alertKey,
        // The setup opens in the tap's frame and its fade covers the closing sheet (D-062).
        onPickTea = {
            pickerOpen = false
            navigation.openSetup(it)
        },
        onQuickTimer = {
            pickerOpen = false
            navigation.openQuickTimer()
        },
        onDismissRequest = { pickerOpen = false },
        onCatalog = {
            pickerOpen = false
            tab.openCatalogForBrew()
        },
        viewModel = pickerViewModel,
    )
}

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
