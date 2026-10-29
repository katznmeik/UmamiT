package com.umamit.feature.brew.setup

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SeedData
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.InventoryEventType
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.engine.PlannedStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Bestand korrigieren* from the setup (BRW-02, INV-03, INV-05; spec #81 seam 5): „… g übrig" opens
 * „Wie viel ist noch da?", *Speichern* stores the new total and the header shows it at once; invalid
 * input is refused; an alert removes the sheet (rule 8). The setup runs with its real view model on
 * fake repositories.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class BrewSetupStockTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val starter = FakeBrewStarter()

    @Before
    fun seed() = runBlocking {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        SeedData.vessels.forEach { collection.vessels.saveVessel(it) }
        SeedData.recipes.forEach { collection.recipes.saveRecipe(it) }
        // Hisui's 100 g package.
        SeedData.initialStock(0).forEach { collection.stock.addEvent(it) }
    }

    private fun show() {
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
            UmamiTheme(reduceMotion = true) {
                BrewSetupRoute(onBack = {}, onOpenTimer = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun openSheet() {
        compose.onNodeWithTag(SETUP_STOCK_TAG).performClick()
        compose.onNodeWithText("Wie viel ist noch da?").assertIsDisplayed()
    }

    private fun field() = compose.onNode(hasSetTextAction())

    /** The number in the field, without its label and the „g". */
    private fun assertField(text: String) =
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))

    private fun save() = compose.onNodeWithTag(STOCK_SHEET_SAVE_TAG)

    private fun hisuiEvents() = runBlocking { collection.stock.observeEvents(SeedData.HISUI_TEA_ID).first() }

    @Test
    fun `the new total 23,5 g is stored as one correction and the setup shows it at once (INV-03)`() {
        show()
        compose.onNodeWithTag(SETUP_STOCK_TAG).assertTextEquals("100,0 g übrig")

        openSheet()
        // It opens on the current stock.
        assertField("100,0")
        field().performTextReplacement("23,5")
        save().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Wie viel ist noch da?").assertDoesNotExist()
        compose.onNodeWithTag(SETUP_STOCK_TAG).assertTextEquals("23,5 g übrig")
        val adjustment = hisuiEvents().single { it.type == InventoryEventType.ADJUSTMENT }
        assertThat(adjustment.deltaGrams).isWithin(1e-9).of(-76.5)
    }

    @Test
    fun `0 g is allowed and shows the tea as leer (INV-05)`() {
        show()
        openSheet()

        field().performTextReplacement("0")
        save().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(SETUP_STOCK_TAG).assertTextEquals("Leer")
    }

    @Test
    fun `the steppers change the total by 0,1 g, never below 0`() {
        show()
        openSheet()

        compose.onNodeWithContentDescription("0,1 g mehr").performClick()
        assertField("100,1")
        field().performTextReplacement("0,1")
        compose.onNodeWithContentDescription("0,1 g weniger").performClick()
        assertField("0,0")
        compose.onNodeWithContentDescription("0,1 g weniger").assertIsNotEnabled()
        save().assertIsEnabled()
    }

    @Test
    fun `a dot works as the decimal separator too`() {
        show()
        openSheet()

        field().performTextReplacement("12.5")
        save().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(SETUP_STOCK_TAG).assertTextEquals("12,5 g übrig")
    }

    @Test
    fun `negative, non-numeric, too precise or empty input is refused and nothing is stored`() {
        show()
        openSheet()

        listOf("-1", "abc", "23,45", "1.000", "10000", "2,5,1").forEach { input ->
            field().performTextReplacement(input)
            save().assertIsNotEnabled()
            compose.onNodeWithText("Gib eine Menge ab 0 g ein, z. B. 23,5").assertIsDisplayed()
        }
        field().performTextReplacement("")
        save().assertIsNotEnabled()

        assertThat(hisuiEvents().map { it.type }).doesNotContain(InventoryEventType.ADJUSTMENT)
    }

    @Test
    fun `Abbrechen closes the sheet and keeps the stock`() {
        show()
        openSheet()

        field().performTextReplacement("5")
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Wie viel ist noch da?").assertDoesNotExist()
        compose.onNodeWithTag(SETUP_STOCK_TAG).assertTextEquals("100,0 g übrig")
        assertThat(hisuiEvents().map { it.type }).doesNotContain(InventoryEventType.ADJUSTMENT)
    }

    @Test
    fun `an alert removes the sheet at once, without saving (rule 8)`() {
        val waiting = FakeBrewStarter.readyState(tanabata(), "other")
        starter.state.value = waiting.copy(phase = TimerPhase.RUNNING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        show()
        openSheet()
        field().performTextReplacement("5")

        compose.mainClock.autoAdvance = false
        starter.state.value = waiting.copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Wie viel ist noch da?").assertDoesNotExist()
        assertThat(hisuiEvents().map { it.type }).doesNotContain(InventoryEventType.ADJUSTMENT)
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
