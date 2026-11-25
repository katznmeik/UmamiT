package com.umamit.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.designsystem.theme.UmamiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Vollbild sheet in the root (TMR-31, SCR-34): never over a timer screen or an alert, answered once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class FsiPromptFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val running =
        ActiveBrew(title = "Hisui", step = "Aufguss 1/3", remainingSeconds = 30, liquorColor = Color.Green)
    private val brew = MutableStateFlow<ActiveBrew?>(running)
    private val alertKey = MutableStateFlow<String?>(null)

    private val source = object : ActiveBrewSource {
        override val activeBrew: StateFlow<ActiveBrew?> = brew
        override val progress: () -> Float = { 0.5f }
    }

    private val prompt = FakePrompt()

    private class FakePrompt : FsiPrompt {
        override val promptedVersionCode = MutableStateFlow<Int?>(0)
        override val currentVersionCode = 1_000
        var allowed = false
        var marks = 0
        var opened = 0
        var reads = 0

        override fun fullScreenAllowed(): Boolean {
            reads++
            return allowed
        }

        override fun markPrompted() {
            marks++
            promptedVersionCode.value = currentVersionCode
        }

        override fun openSystemPage() {
            opened++
        }
    }

    private fun show(onboardingCompleted: Boolean = true) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UmamitRoot(
                    activeBrewSource = source,
                    journalSources = testJournalSources(),
                    alertKey = alertKey,
                    onboardingCompleted = MutableStateFlow(onboardingCompleted),
                    onboardingScreen = { onFinish -> Button(onClick = onFinish) { Text("ONBOARDING-ENDE") } },
                    fsiPrompt = prompt,
                    timerScreen = { onClose, _, _, _ ->
                        Column {
                            Text("TIMER")
                            Button(onClick = onClose) { Text("Session beenden") }
                        }
                    },
                    settings = testSettingsScreens(),
                    startScreen = { tab -> Button(onClick = tab.navigation.openQuickTimer) { Text("Schnell") } },
                    quickTimerScreen = { onBack, _ -> Button(onClick = onBack) { Text("QUICK") } },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun resumeAgain() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    private fun assertSheetShown() = compose.onNodeWithTag(FSI_SHEET_TAG).assertIsDisplayed()

    private fun assertNoSheet() = compose.onNodeWithTag(FSI_SHEET_TAG).assertDoesNotExist()

    @Test
    fun `the sheet shows on open and Später answers it once`() {
        show()
        assertSheetShown()

        compose.onNodeWithTag(FSI_LATER_TAG).performClick()
        compose.waitForIdle()

        assertNoSheet()
        assertThat(prompt.marks).isEqualTo(1)
        assertThat(prompt.opened).isEqualTo(0)
    }

    @Test
    fun `Erlauben stores the versionCode and opens the system page`() {
        show()

        compose.onNodeWithTag(FSI_ALLOW_TAG).performClick()
        compose.waitForIdle()

        assertNoSheet()
        assertThat(prompt.marks).isEqualTo(1)
        assertThat(prompt.opened).isEqualTo(1)
    }

    @Test
    fun `a granted permission or an answered version shows nothing`() {
        prompt.allowed = true
        show()
        assertNoSheet()
        prompt.allowed = false
        prompt.promptedVersionCode.value = 1_000
        resumeAgain()
        assertNoSheet()
    }

    @Test
    fun `the first start shows nothing and stores nothing`() {
        show(onboardingCompleted = false)

        assertNoSheet()
        compose.onNodeWithText("ONBOARDING-ENDE").performClick()
        compose.waitForIdle()
        // Not in the session that began with the setup either.
        assertNoSheet()
        assertThat(prompt.marks).isEqualTo(0)
    }

    @Test
    fun `the sheet waits while a timer screen is on top and shows once it is left`() {
        prompt.allowed = true
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        prompt.allowed = false
        resumeAgain()
        assertNoSheet()

        compose.onNodeWithText("Session beenden").performClick()
        compose.waitForIdle()

        assertSheetShown()
    }

    @Test
    fun `the sheet waits while the quick timer is on top`() {
        prompt.allowed = true
        show()
        compose.onNodeWithText("Schnell").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("QUICK").assertIsDisplayed()
        prompt.allowed = false
        resumeAgain()
        assertNoSheet()

        compose.onNodeWithText("QUICK").performClick()
        compose.waitForIdle()

        assertSheetShown()
    }

    @Test
    fun `an alert removes the sheet and it comes back after the alert`() {
        show()
        assertSheetShown()

        alertKey.value = "a1"
        brew.value = running.copy(alerting = true)
        compose.waitForIdle()
        assertNoSheet()
        assertThat(prompt.marks).isEqualTo(0)

        alertKey.value = null
        brew.value = running
        compose.waitForIdle()
        compose.onNodeWithTag(FSI_SHEET_TAG).assertExists()
    }

    @Test
    fun `a normal open reads the permission once`() {
        show()

        assertSheetShown()
        assertThat(prompt.reads).isEqualTo(1)
        resumeAgain()
        assertThat(prompt.reads).isEqualTo(2)
    }

    @Test
    fun `the permission is not read over a timer screen and is read once it is left`() {
        show()
        compose.onNodeWithText("Hisui", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("TIMER").assertIsDisplayed()
        val before = prompt.reads

        resumeAgain()
        resumeAgain()
        assertThat(prompt.reads).isEqualTo(before)

        compose.onNodeWithText("Session beenden").performClick()
        compose.waitForIdle()

        assertThat(prompt.reads).isEqualTo(before + 1)
        assertSheetShown()
    }

    @Test
    fun `an alert that starts while the app is stopped is not read on its resume`() {
        show()
        val before = prompt.reads
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        // The full-screen intent's resume (rule 8): the alert came while the app was stopped.
        alertKey.value = "a1"
        brew.value = running.copy(alerting = true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        assertThat(prompt.reads).isEqualTo(before)
        assertNoSheet()

        alertKey.value = null
        brew.value = running
        compose.waitForIdle()

        assertThat(prompt.reads).isEqualTo(before + 1)
        compose.onNodeWithTag(FSI_SHEET_TAG).assertExists()
    }
}
