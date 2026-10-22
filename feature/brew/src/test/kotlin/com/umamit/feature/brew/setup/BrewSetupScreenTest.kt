package com.umamit.feature.brew.setup

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SeedData
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The setup (SCR-04) with its real view model on fake repositories and a fake
 * controller (spec #40 seam 4): *Brühen starten* → READY, *Im Rezept
 * speichern* only after a change, the TMR-21 question, and the Schritte-Sheet
 * giving way to an alert (rule 8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class BrewSetupScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val starter = FakeBrewStarter()
    private var timerOpened = 0

    @Before
    fun seed() = runBlocking {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        SeedData.vessels.forEach { collection.vessels.saveVessel(it) }
        SeedData.recipes.forEach { collection.recipes.saveRecipe(it) }
    }

    private fun show(reduceMotion: Boolean = true) {
        val viewModel = BrewSetupViewModel(
            SavedStateHandle(mapOf(BrewSetupViewModel.TEA_ID to SeedData.HISUI_TEA_ID)),
            collection.teas,
            collection.recipes,
            collection.vessels,
            collection.stock,
            sessions.sessions,
            settings,
            starter,
        )
        compose.setContent {
            UmamiTheme(reduceMotion = reduceMotion) {
                BrewSetupRoute(onBack = {}, onOpenTimer = { timerOpened++ }, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun tapStart() = compose.onNodeWithTag(SETUP_START_TAG).performScrollTo().performClick()

    @Test
    fun `Bruehen starten starts the session in READY and opens the timer (TMR-01)`() {
        show()
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithText("Wasserkocher auf 65 °C").assertIsDisplayed()

        tapStart()
        compose.waitForIdle()

        assertThat((starter.state.value as TimerUiState.Active).phase).isEqualTo(TimerPhase.READY)
        assertThat(starter.starts.single().snapshot.leafGrams).isEqualTo(5.0)
        assertThat(timerOpened).isEqualTo(1)
    }

    @Test
    fun `Im Rezept speichern shows only after a change, then confirms in place (BRW-02)`() {
        show()
        compose.onNodeWithTag(SETUP_SAVE_TAG).assertDoesNotExist()

        compose.onNodeWithContentDescription("Mehr Wasser").performClick()
        compose.onNodeWithText("255\u00A0ml").assertIsDisplayed()
        compose.onNodeWithText("5,1\u00A0g").assertIsDisplayed()
        compose.onNodeWithTag(SETUP_SAVE_TAG).performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(SETUP_SAVE_TAG).assertDoesNotExist()
        compose.onNodeWithText("Im Rezept gespeichert").assertIsDisplayed()
        val saved = runBlocking { collection.recipes.getRecipe(SeedData.recipes[2].id)!! }
        assertThat(saved.waterMl).isEqualTo(255)
    }

    @Test
    fun `Bruehen starten does not move under the finger after Im Rezept speichern (D-058)`() {
        show(reduceMotion = false)
        compose.onNodeWithContentDescription("Mehr Wasser").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SETUP_SAVE_TAG).performScrollTo()
        compose.waitForIdle()
        val before = compose.onNodeWithTag(SETUP_START_TAG).fetchSemanticsNode().boundsInRoot

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(SETUP_SAVE_TAG).performClick()
        // Every frame of the swap to „Im Rezept gespeichert": the start button stays where it was.
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertThat(compose.onNodeWithTag(SETUP_START_TAG).fetchSemanticsNode().boundsInRoot).isEqualTo(before)
        }
    }

    @Test
    fun `the ratio lock is one switch node with its state and the ratio (03 §6)`() {
        show()

        compose.onNode(hasText("Verhältnis halten") and isToggleable())
            .assertIsOn()
            .assert(hasText("2,0\u00A0g / 100\u00A0ml"))
            .performClick()
            .assertIsOff()
        // Label and value of a stepper row are one node.
        compose.onNode(hasText("Wasser") and hasText("250\u00A0ml")).assertExists()
    }

    @Test
    fun `a start refused during an alert opens the timer instead of asking (rule 8)`() {
        starter.state.value = FakeBrewStarter.readyState(tanabata(), "other")
            .copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        show()

        tapStart()
        compose.waitForIdle()

        compose.onNodeWithText("Erst die Bio Sencha Tanabata-Sitzung beenden?").assertDoesNotExist()
        assertThat(timerOpened).isEqualTo(1)
        assertThat(starter.starts).isEmpty()
    }

    @Test
    fun `a second session asks first - Beenden und neu starten replaces it (TMR-21)`() {
        starter.state.value = FakeBrewStarter.readyState(tanabata(), "other")
        show()

        tapStart()
        compose.onNodeWithText("Erst die Bio Sencha Tanabata-Sitzung beenden?").assertIsDisplayed()
        assertThat(timerOpened).isEqualTo(0)

        compose.onNodeWithText("Beenden & neu starten").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Erst die Bio Sencha Tanabata-Sitzung beenden?").assertDoesNotExist()
        assertThat(starter.replaced).isEqualTo(1)
        assertThat(timerOpened).isEqualTo(1)
    }

    @Test
    fun `Abbrechen keeps the active session`() {
        starter.state.value = FakeBrewStarter.readyState(tanabata(), "other")
        show()

        tapStart()
        compose.onNodeWithText("Abbrechen").performClick()

        compose.onNodeWithText("Erst die Bio Sencha Tanabata-Sitzung beenden?").assertDoesNotExist()
        assertThat(starter.starts).isEmpty()
    }

    @Test
    fun `Bearb opens the Schritte-Sheet, and an alert removes it at once, without the close animation (rule 8)`() {
        val waiting = FakeBrewStarter.readyState(tanabata(), "other")
        starter.state.value = waiting.copy(phase = TimerPhase.RUNNING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        // With motion on, a normal close would still be sliding after two frames; only the alert gate is instant.
        show(reduceMotion = false)

        compose.onNodeWithTag(SETUP_STEPS_EDIT_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Schritte für diese Sitzung").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        starter.state.value = waiting.copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        // The frame that recomposes, and the frame that drops the sheet's window, without its close animation.
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Schritte für diese Sitzung").assertDoesNotExist()
    }

    @Test
    fun `the pre-alert row opens the Schritte-Sheet too (SCR-04)`() {
        show()
        compose.onNodeWithText("10 s (Standard)").assertIsDisplayed()

        compose.onNodeWithTag(SETUP_PRE_ALERT_TAG).performScrollTo().performClick()

        compose.onNodeWithText("Schritte für diese Sitzung").assertIsDisplayed()
    }

    private fun tanabata() = BrewStart(
        snapshot = SessionSnapshot(
            SeedData.TANABATA_TEA_ID, "Bio Sencha Tanabata", null, "Sencha", "sencha", null, null, "Kyusu",
            BrewMethod.KYUSU, SeedData.KYUSU_VESSEL_ID, "Kyusu", 5.0, 250, 65,
        ),
        steps = listOf(PlannedStep(StepKind.INFUSION, 45, 65)),
        recipePreAlertSeconds = null,
    )
}
