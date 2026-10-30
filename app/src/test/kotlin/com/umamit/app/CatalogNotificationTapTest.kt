package com.umamit.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.catalog.CatalogRefreshIntents
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogOverview
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.catalogTea
import com.umamit.feature.catalog.CATALOG_REFRESH_SHEET_TAG
import com.umamit.feature.catalog.CATALOG_REFRESH_SUMMARY_TAG
import com.umamit.feature.catalog.CatalogRoute
import com.umamit.feature.catalog.CatalogViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tap on the catalog refresh's notifications (v0.2.2, #113; SCR-22, 03 §2.4, D-097): the content
 * intent both notifications carry reaches MainActivity, whose requests ([CatalogRefreshRequests])
 * the real root and shell turn into Sammlung › Katalog with the refresh sheet over whatever was
 * shown: the progress while the refresh runs, the summary after it, the last one from the refresh
 * log when the app was closed since. Back returns to what was shown, a timer on screen included.
 * An alert wins (rule 8, D-077), and before the first-start setup is done the app opens where it
 * was (ONB-03). The real catalog view model on fakes; invented tea (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogNotificationTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val catalog = FakeCatalogRepository(
        initial = CatalogOverview(listOf(catalogTea("XY0100", "Morgennebel Sencha")), lastRefreshAtMillis = 1L),
    )
    private val runner = FakeCatalogRefreshRunner()
    private val requests = CatalogRefreshRequests(newToken = { ++tokens })
    private var tokens = 0L
    private val alertKey = MutableStateFlow<String?>(null)
    private val completed = MutableStateFlow<Boolean?>(true)
    private val brew = MutableStateFlow<ActiveBrew?>(null)

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
    }

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    onboardingCompleted = completed,
                    onboardingScreen = { Text("EINRICHTUNG") },
                    catalogRefreshRequest = requests.token,
                    onConsumeCatalogRefreshRequest = requests::handled,
                    timerScreen = { _, _, _ -> Text("TIMER") },
                    settings = testSettingsScreens(),
                    catalogScreen = { tab -> Catalog(tab) },
                )
            }
        }
        compose.waitForIdle()
    }

    /** The app's catalog segment on the fakes, wired as [CatalogTabContent] wires it. */
    @Composable
    private fun Catalog(tab: CatalogTab) {
        CatalogRoute(
            alerting = tab.alerting,
            onOpenTea = tab.openTea,
            modifier = tab.modifier,
            refreshSheetRequest = tab.refreshSheetRequest,
            viewModel = viewModel { CatalogViewModel(catalog, runner) },
        )
    }

    /** The notification's content intent, as MainActivity gets it in `onCreate` or `onNewIntent`. */
    private fun tapNotification() {
        val intent = CatalogRefreshIntents.openRefresh(compose.activity)
        compose.runOnUiThread { assertThat(requests.offer(intent)).isTrue() }
        compose.waitForIdle()
    }

    private fun sheet() = compose.onNodeWithTag(CATALOG_REFRESH_SHEET_TAG)

    private fun catalogShown() {
        compose.onNodeWithTag(tabTag(Tab.Collection)).assertIsSelected()
        compose.onNodeWithText("Morgennebel Sencha").assertExists()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun theIntentNamesTheAppsOwnActivity() {
        val intent = CatalogRefreshIntents.openRefresh(compose.activity)

        assertThat(intent.component).isEqualTo(ComponentName(compose.activity, MainActivity::class.java))
        assertThat(intent.`package`).isEqualTo(compose.activity.packageName)
    }

    @Test
    fun theProgressNotificationOpensTheCatalogWithTheSheetShowingTheProgress() {
        runner.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(120, 1560, 40))
        show()

        tapNotification()

        catalogShown()
        sheet().assertIsDisplayed()
        compose.onNodeWithText("Katalog wird geladen").assertIsDisplayed()
        compose.onNodeWithText("Seiten: 120 von 1560 · Produkte: 40").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").assertIsDisplayed()
        // Nothing was started: the sheet only shows the refresh.
        assertThat(runner.starts).isEqualTo(0)
    }

    @Test
    fun theResultNotificationOpensTheCatalogWithTheSummary() {
        runner.state.value = CatalogRefreshState.Finished(SUCCESS)
        show()

        tapNotification()

        catalogShown()
        compose.onNodeWithTag(CATALOG_REFRESH_SUMMARY_TAG).assertTextEquals(SUMMARY)
    }

    @Test
    fun afterTheAppWasClosedTheSummaryComesFromTheRefreshLog() {
        // A new process: the runner holds nothing, the log has the last refresh.
        runner.lastResult = SUCCESS
        show()

        tapNotification()

        catalogShown()
        compose.onNodeWithTag(CATALOG_REFRESH_SUMMARY_TAG).assertTextEquals(SUMMARY)
    }

    @Test
    fun backReturnsToTheScreenThatWasShown() {
        runner.state.value = CatalogRefreshState.Finished(SUCCESS)
        show()
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()

        tapNotification()
        catalogShown()
        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()
        back()

        compose.onNodeWithTag(tabTag(Tab.Journal)).assertIsSelected()
        sheet().assertDoesNotExist()
    }

    @Test
    fun aTimerOnScreenStaysInTheBackStackAndKeepsRunning() {
        brew.value = RUNNING
        runner.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(3, 10, 2))
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()

        tapNotification()
        catalogShown()
        sheet().assertIsDisplayed()
        // Back from the catalog (the sheet, a dialog window, takes the first back on the phone itself):
        // the timer is there again, its session untouched.
        back()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        assertThat(brew.value).isEqualTo(RUNNING)
        assertThat(runner.cancels).isEqualTo(0)
        // The running refresh goes on behind the closed sheet.
        assertThat(runner.state.value).isInstanceOf(CatalogRefreshState.Running::class.java)
    }

    /**
     * #121: an older refresh shell lower in the back stack must not take a newer tap's token when back
     * returns to it (its sheet again, its tabs reset), and the shells do not pile up: the new tap replaces
     * the older shell, and a timer opened from it stays below the new one.
     */
    @Test
    fun aNewTapReplacesAnOlderRefreshShellAndKeepsTheTimerAboveIt() {
        brew.value = RUNNING
        runner.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(3, 10, 2))
        show()
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()
        tapNotification()
        sheet().assertIsDisplayed()
        // From inside the refresh shell: the timer, through the active brew bar.
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()

        runner.state.value = CatalogRefreshState.Finished(SUCCESS)
        tapNotification()
        catalogShown()
        compose.onNodeWithTag(CATALOG_REFRESH_SUMMARY_TAG).assertTextEquals(SUMMARY)
        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()

        back()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        back()
        // The app's shell as it was, not an older refresh shell taking the newer tap again.
        compose.onNodeWithTag(tabTag(Tab.Journal)).assertIsSelected()
        sheet().assertDoesNotExist()
        compose.onNodeWithTag(CATALOG_REFRESH_SUMMARY_TAG).assertDoesNotExist()
    }

    @Test
    fun aSecondTapWhileTheRefreshShellShowsTurnsItBackToTheCatalogWithTheSheet() {
        runner.state.value = CatalogRefreshState.Finished(SUCCESS)
        // Seen once with *Fertig*, the result is still in the refresh log for the second tap.
        runner.lastResult = SUCCESS
        show()
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()
        tapNotification()
        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()
        // Inside the refresh shell, another tab.
        compose.onNodeWithTag(tabTag(Tab.Stats)).performClick()
        compose.waitForIdle()

        tapNotification()

        catalogShown()
        compose.onNodeWithTag(CATALOG_REFRESH_SUMMARY_TAG).assertTextEquals(SUMMARY)
        compose.onNodeWithText("Fertig").performClick()
        compose.waitForIdle()
        // One refresh shell: one back returns to the app's shell as it was.
        back()
        compose.onNodeWithTag(tabTag(Tab.Journal)).assertIsSelected()
        sheet().assertDoesNotExist()
    }

    @Test
    fun duringAnAlertNoSheetOpensAndTheCatalogShowsAfterIt() {
        brew.value = RUNNING.copy(alerting = true)
        alertKey.value = "A"
        runner.state.value = CatalogRefreshState.Finished(SUCCESS)
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        tapNotification()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        sheet().assertDoesNotExist()

        alertKey.value = null
        brew.value = RUNNING
        compose.waitForIdle()

        catalogShown()
        compose.onNodeWithTag(CATALOG_REFRESH_SUMMARY_TAG).assertTextEquals(SUMMARY)
        back()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }

    @Test
    fun anAlertThatStartsOverTheSheetTakesItsPlace() {
        brew.value = RUNNING
        runner.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(3, 10, 2))
        show()
        tapNotification()
        sheet().assertIsDisplayed()

        alertKey.value = "A"
        brew.value = RUNNING.copy(alerting = true)
        compose.waitForIdle()

        compose.onNodeWithText("TIMER").assertIsDisplayed()
        sheet().assertDoesNotExist()
    }

    @Test
    fun beforeTheSetupIsFinishedTheAppOpensWhereItWas() {
        completed.value = false
        runner.state.value = CatalogRefreshState.Running(CatalogRefreshProgress(3, 10, 2))
        show()
        compose.onNodeWithText("EINRICHTUNG").assertIsDisplayed()

        tapNotification()

        compose.onNodeWithText("EINRICHTUNG").assertIsDisplayed()
        sheet().assertDoesNotExist()
        // The tap is used up: finishing the setup later opens no catalog.
        assertThat(requests.pending).isEqualTo(0L)
    }

    @Test
    fun onlyTheAppsOwnFreshIntentIsARequest() {
        val own = CatalogRefreshIntents.openRefresh(compose.activity)

        assertThat(requests.offer(Intent(Intent.ACTION_MAIN))).isFalse()
        assertThat(requests.offer(null)).isFalse()
        // A task reopened from the recent apps brings its first intent again: no new sheet.
        assertThat(requests.offer(Intent(own).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))).isFalse()
        assertThat(requests.pending).isEqualTo(0L)
        assertThat(requests.offer(own)).isTrue()
        assertThat(requests.pending).isNotEqualTo(0L)
    }

    @Test
    fun aPendingTapSurvivesTheActivitysRecreation() {
        requests.offer(CatalogRefreshIntents.openRefresh(compose.activity))
        val state = Bundle().also(requests::save)

        val recreated = CatalogRefreshRequests()
        recreated.restore(state)

        assertThat(recreated.pending).isEqualTo(requests.pending)
        recreated.handled(recreated.pending)
        assertThat(recreated.pending).isEqualTo(0L)
    }

    private companion object {
        val SUCCESS = CatalogRefreshOutcome.Success(teas = 276, newTeas = 12)
        const val SUMMARY = "276 Tees · 12 neu"
        val RUNNING =
            ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green)
    }
}
