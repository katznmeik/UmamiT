package com.umamit.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SeedPhotoProgress
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeCatalogRefreshRunner
import com.umamit.core.testing.FakeFirstStartRunner
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.testing.FakeTimerReliability
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first-start setup (SCR-01; ONB-01…04, ONB-06): welcome → reliability → catalog → done, the
 * ✓/! rows following the checks, the photo line with *Überspringen*, *Katalog jetzt laden* with
 * its progress inline or *Später* (ONB-03), and the run-once marker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class OnboardingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val reliability = FakeTimerReliability(ReliabilityCheck.Battery)
    private val firstStart = FakeFirstStartRunner()
    private val settings = FakeSettingsRepository()
    private val catalog = FakeCatalogRefreshRunner()
    private var finished = 0

    private fun show(): OnboardingViewModel {
        val viewModel = OnboardingViewModel(reliability, firstStart, settings, catalog)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                OnboardingRoute(onFinish = { finished++ }, viewModel = viewModel)
            }
        }
        return viewModel
    }

    /** Welcome → reliability → the catalog page (ONB-03). */
    private fun toCatalogPage() {
        compose.onNodeWithText("Los geht's").performClick()
        compose.onNodeWithText("Weiter").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the welcome leads through the reliability page to the catalog page, and Spaeter finishes once`() =
        runBlocking {
            show()

            compose.onNodeWithText("Aus Liebe zum Tee").assertIsDisplayed()
            compose.onNodeWithText("Los geht's").performClick()
            compose.onNodeWithText("Damit der Timer zuverlässig klingelt").assertIsDisplayed()
            compose.onNodeWithText("Weiter").performScrollTo().performClick()
            compose.waitForIdle()
            assertThat(finished).isEqualTo(0)
            compose.onNodeWithText("Katalog laden").assertIsDisplayed()
            compose.onNodeWithText("Später").performScrollTo().performClick()
            compose.onNodeWithText("Später").performClick()
            compose.waitForIdle()

            assertThat(finished).isEqualTo(1)
            assertThat(settings.settings.first().onboardingCompleted).isTrue()
            // *Später* loads nothing (CAT-07 AC7: only on a tap of its own).
            assertThat(catalog.starts).isEqualTo(0)
        }

    @Test
    fun `Katalog jetzt laden starts the refresh, shows it inline, and Weiter finishes while it runs`() {
        show()
        toCatalogPage()

        compose.onNodeWithText("Katalog jetzt laden").performScrollTo().performClick()
        catalog.state.value =
            CatalogRefreshState.Running(CatalogRefreshProgress(pagesDone = 2, pagesTotal = 40, products = 1))
        compose.waitForIdle()

        assertThat(catalog.starts).isEqualTo(1)
        compose.onNodeWithText("Seiten: 2 von 40 · Produkte: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Katalog jetzt laden").assertDoesNotExist()
        compose.onNodeWithText("Später").assertDoesNotExist()
        compose.onNodeWithText("Weiter").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(finished).isEqualTo(1)
        // The refresh goes on in its service; finishing the setup never stops it.
        assertThat(catalog.cancels).isEqualTo(0)
    }

    @Test
    fun `the page says how the load ended`() {
        show()
        toCatalogPage()
        compose.onNodeWithText("Katalog jetzt laden").performScrollTo().performClick()

        catalog.state.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(teas = 274, newTeas = 274))
        compose.waitForIdle()
        compose.onNodeWithText("Katalog geladen: 274\u00A0Tees").performScrollTo().assertIsDisplayed()

        catalog.state.value = CatalogRefreshState.Finished(CatalogRefreshOutcome.Offline("details"))
        compose.waitForIdle()
        compose.onNodeWithText("Der Katalog konnte gerade nicht geladen werden", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `back on the catalog page returns to the reliability page`() {
        show()
        toCatalogPage()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText("Damit der Timer zuverlässig klingelt").assertIsDisplayed()
    }

    @Test
    fun `a failed write keeps the setup open with a note, and Spaeter tries again`() = runBlocking {
        show()
        toCatalogPage()
        settings.failUpdates = true

        compose.onNodeWithText("Später").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(finished).isEqualTo(0)
        compose.onNodeWithText("Konnte nicht gespeichert werden", substring = true).performScrollTo()
            .assertIsDisplayed()

        settings.failUpdates = false
        compose.onNodeWithText("Später").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(finished).isEqualTo(1)
        assertThat(settings.settings.first().onboardingCompleted).isTrue()
    }

    @Test
    fun `the first start runs in the background as soon as the setup opens`() {
        show()

        assertThat(firstStart.starts).isEqualTo(1)
    }

    @Test
    fun `the rows show the checks, a missing one opens its page, and they follow on return`() {
        show()
        compose.onNodeWithText("Los geht's").performClick()

        compose.onNodeWithTag(OnboardingTags.row(ReliabilityCheck.Battery)).assertIsDisplayed()
        compose.onNodeWithText("Freigeben").performClick()
        assertThat(reliability.opened).containsExactly(ReliabilityCheck.Battery)

        // Back from the system settings: the screen resumes and reads the checks again.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        reliability.set(ReliabilityCheck.Battery, CheckState.Ok)
        reliability.set(ReliabilityCheck.FullScreenAlert, CheckState.Missing)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("Freigeben").assertDoesNotExist()
        compose.onNodeWithText("Erlauben").assertIsDisplayed()
    }

    @Test
    fun `the Samsung auto-restart hint is shown below the rows`() {
        show()
        compose.onNodeWithText("Los geht's").performClick()

        compose.onNodeWithText("Gerätewartung öffnen").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Katalog jetzt laden ends the seed photo download first, so two shop jobs never overlap`() {
        // Rule 7 (D-090): a stop of one job must not leave the other sending; CAT-15 gives the covers.
        firstStart.photoProgress.value = SeedPhotoProgress.Running(current = 1, total = 2)
        show()
        toCatalogPage()

        compose.onNodeWithText("Katalog jetzt laden").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(firstStart.skips).isEqualTo(1)
        assertThat(catalog.starts).isEqualTo(1)
    }

    @Test
    fun `while the photos load, the line shows, and Ueberspringen stops them`() {
        firstStart.photoProgress.value = SeedPhotoProgress.Running(current = 1, total = 2)
        show()
        compose.onNodeWithText("Los geht's").performClick()

        compose.onNodeWithText("Teefotos werden geladen …").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()

        assertThat(firstStart.skips).isEqualTo(1)
        compose.onNodeWithText("Teefotos werden geladen …").assertDoesNotExist()
    }

    @Test
    fun `a setup shown again from Ueber keeps the marker set`() = runBlocking {
        settings.update { it.copy(onboardingCompleted = true) }
        show()
        toCatalogPage()
        compose.onNodeWithText("Später").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(settings.settings.first()).isEqualTo(UserSettings(onboardingCompleted = true))
        assertThat(finished).isEqualTo(1)
    }
}
