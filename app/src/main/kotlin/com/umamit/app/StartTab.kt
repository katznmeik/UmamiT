package com.umamit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.umamit.feature.brew.picker.TeaPickerHost
import com.umamit.feature.home.HomeActions
import com.umamit.feature.home.HomeRoute
import com.umamit.feature.home.HomeScreen
import com.umamit.feature.home.HomeUiState

/** Where Start leads outside its tab (03 §2.1): the setup, the timer and the quick timer. */
internal class StartNavigation(
    val openSetup: (teaId: String) -> Unit = {},
    val openTimer: () -> Unit = {},
    val openQuickTimer: () -> Unit = {},
)

/**
 * What the shell hands the Start tab: its own actions and the root's [navigation].
 *
 * @property alertKey the alert playing now, or null: the tea picker never covers it (rule 8).
 */
internal class StartTab(
    val openSettings: () -> Unit,
    val scrollToTopRequest: Int,
    val showJournal: () -> Unit,
    val openCatalog: () -> Unit,
    val alertKey: String?,
    val navigation: StartNavigation,
)

/** Start with its view model and the tea picker (SCR-02, SCR-03): the app's Start tab. */
@Composable
internal fun StartTabContent(tab: StartTab) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpenedIn by rememberSaveable { mutableStateOf<String?>(null) }
    val navigation = tab.navigation
    HomeRoute(
        actions = HomeActions(
            openSettings = tab.openSettings,
            startBrew = {
                pickerOpenedIn = tab.alertKey
                pickerOpen = true
            },
            openTimer = navigation.openTimer,
            quickTimer = navigation.openQuickTimer,
            openSetup = navigation.openSetup,
            showJournal = tab.showJournal,
            openCatalog = tab.openCatalog,
        ),
        onOpenTimer = navigation.openTimer,
        scrollToTopRequest = tab.scrollToTopRequest,
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
