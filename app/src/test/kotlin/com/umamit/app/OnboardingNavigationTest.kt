package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.umamit.core.designsystem.R as DesignSystemR

/**
 * The first-start setup in the app's navigation (SCR-01, ONB-04): it opens first until it is
 * completed, then never again on its own; *Über › Einrichtung erneut zeigen* runs it again, and
 * like every route above the shell it gives way to a new alert (rule 8, D-062).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class OnboardingNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val completed = MutableStateFlow<Boolean?>(null)
    private val alertKey = MutableStateFlow<String?>(null)
    private var setupsFinished = 0

    private fun show() = compose.setContent {
        UmamiTheme(reduceMotion = true) {
            UmamitRoot(
                activeBrewSource = NoActiveBrewSource,
                journalSources = testJournalSources(),
                alertKey = alertKey,
                onboardingCompleted = completed,
                onboardingScreen = { onFinish ->
                    Button(onClick = {
                        setupsFinished++
                        completed.value = true
                        onFinish()
                    }) { Text("EINRICHTUNG FERTIG") }
                },
                timerScreen = { _, _, _, _ -> Text("TIMER") },
                startScreen = { Text("START") },
                settings = testSettingsScreens(),
            )
        }
    }

    private fun str(id: Int) = compose.activity.getString(id)

    @Test
    fun `nothing shows until the marker is read, then the setup opens first`() {
        show()
        compose.onNodeWithText("START").assertDoesNotExist()
        compose.onNodeWithText("EINRICHTUNG FERTIG").assertDoesNotExist()

        completed.value = false
        compose.waitForIdle()

        compose.onNodeWithText("EINRICHTUNG FERTIG").assertIsDisplayed()
    }

    @Test
    fun `finishing the setup opens Start, and back does not return to the setup`() {
        completed.value = false
        show()

        compose.onNodeWithText("EINRICHTUNG FERTIG").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("START").assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText("EINRICHTUNG FERTIG").assertDoesNotExist()
        assertThat(setupsFinished).isEqualTo(1)
    }

    @Test
    fun `a completed setup opens on Start`() {
        completed.value = true
        show()

        compose.onNodeWithText("START").assertIsDisplayed()
        compose.onNodeWithText("EINRICHTUNG FERTIG").assertDoesNotExist()
    }

    private fun rerunFromAbout() {
        compose.onNodeWithText("START").assertIsDisplayed()
        // The test's Start has no gear; the Journal tab's ⋮ opens the same settings.
        tabSettings()
        compose.onNodeWithText("Über UmamiT").performScrollTo().performClick()
        compose.onNodeWithText("Einrichtung erneut zeigen").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun tabSettings() {
        compose.onNodeWithText(str(R.string.tab_journal)).performClick()
        compose.onNodeWithContentDescription(str(DesignSystemR.string.ds_more_options)).performClick()
        compose.onNodeWithText(str(DesignSystemR.string.ds_settings)).performClick()
    }

    @Test
    fun `Einrichtung erneut zeigen runs the setup again and returns to Ueber`() {
        completed.value = true
        show()

        rerunFromAbout()
        compose.onNodeWithText("EINRICHTUNG FERTIG").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Einrichtung erneut zeigen").assertIsDisplayed()
        assertThat(setupsFinished).isEqualTo(1)
    }

    @Test
    fun `a new alert replaces the setup shown again with the timer`() {
        completed.value = true
        show()
        rerunFromAbout()
        compose.onNodeWithText("EINRICHTUNG FERTIG").assertIsDisplayed()

        alertKey.value = "s1:0:1"
        compose.waitForIdle()

        compose.onNodeWithText("EINRICHTUNG FERTIG").assertDoesNotExist()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
    }
}
