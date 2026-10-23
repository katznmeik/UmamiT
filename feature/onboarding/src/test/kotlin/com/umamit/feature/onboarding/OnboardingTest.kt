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
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.UserSettings
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
 * The first-start setup (SCR-01; ONB-01, ONB-02, ONB-04, ONB-06): welcome → reliability → done,
 * the ✓/! rows following the checks, the photo line with *Überspringen*, and the run-once marker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class OnboardingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val reliability = FakeTimerReliability(ReliabilityCheck.Battery)
    private val firstStart = FakeFirstStartRunner()
    private val settings = FakeSettingsRepository()
    private var finished = 0

    private fun show(): OnboardingViewModel {
        val viewModel = OnboardingViewModel(reliability, firstStart, settings)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                OnboardingRoute(onFinish = { finished++ }, viewModel = viewModel)
            }
        }
        return viewModel
    }

    @Test
    fun `the welcome leads to the reliability page, and Weiter finishes the setup once`() = runBlocking {
        show()

        compose.onNodeWithText("Aus Liebe zum Tee").assertIsDisplayed()
        compose.onNodeWithText("Los geht's").performClick()
        compose.onNodeWithText("Damit der Timer zuverlässig klingelt").assertIsDisplayed()
        compose.onNodeWithText("Weiter").performScrollTo().performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()

        assertThat(finished).isEqualTo(1)
        assertThat(settings.settings.first().onboardingCompleted).isTrue()
    }

    @Test
    fun `a failed write keeps the setup open with a note, and Weiter tries again`() = runBlocking {
        show()
        compose.onNodeWithText("Los geht's").performClick()
        settings.failUpdates = true

        compose.onNodeWithText("Weiter").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(finished).isEqualTo(0)
        compose.onNodeWithText("Konnte nicht gespeichert werden", substring = true).performScrollTo()
            .assertIsDisplayed()

        settings.failUpdates = false
        compose.onNodeWithText("Weiter").performScrollTo().performClick()
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
        compose.onNodeWithText("Los geht's").performClick()
        compose.onNodeWithText("Weiter").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(settings.settings.first()).isEqualTo(UserSettings(onboardingCompleted = true))
        assertThat(finished).isEqualTo(1)
    }
}
