package com.umamit.feature.brew.setup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
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
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.feature.brew.setup.BrewSetupScreenshotTest.Companion.kyusu
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
    private val catalog = FakeCatalogRepository()
    private var timerOpened = 0
    private val catalogOpened = mutableListOf<String>()

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
            catalog,
        )
        compose.setContent {
            UmamiTheme(reduceMotion = reduceMotion) {
                BrewSetupRoute(
                    onBack = {},
                    onOpenTimer = { timerOpened++ },
                    onOpenCatalog = { catalogOpened += it },
                    viewModel = viewModel,
                )
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
    fun `a tea flagged Rezept pruefen shows the flag with its reason above the steps (CAT-05, SCR-04)`() {
        runBlocking {
            val hisui = collection.teas.getTea(SeedData.HISUI_TEA_ID)!!
            collection.teas.saveTea(hisui.copy(recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT))
        }
        show()

        compose.onNodeWithText("Rezept prüfen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Der Shop nennt nicht alle Werte – die fehlenden stammen aus den Standardwerten der Sorte.",
        ).performScrollTo().assertIsDisplayed()
        val note = compose.onNodeWithTag(SETUP_RECIPE_CHECK_TAG).fetchSemanticsNode().boundsInRoot
        val steps = compose.onNodeWithTag(SETUP_STEPS_EDIT_TAG).fetchSemanticsNode().boundsInRoot
        assertThat(note.bottom).isAtMost(steps.top)
    }

    @Test
    fun `a tea not flagged shows no Rezept pruefen`() {
        show()

        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithText("Rezept prüfen").assertDoesNotExist()
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
    fun `a catalog tea's setup offers Im Katalog ansehen in its overflow menu (SCR-04, D-098)`() {
        catalog.details.value = mapOf("TG0557" to CatalogTeaDetail(key = "TG0557", name = "Hisui", productUrl = SHOP))
        show()

        compose.onNodeWithContentDescription("Weitere Optionen").assertIsDisplayed().performClick()
        compose.onNodeWithText("Im Katalog ansehen").assert(hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()

        assertThat(catalogOpened).containsExactly("TG0557")
        compose.onNodeWithText("Im Katalog ansehen").assertDoesNotExist()
    }

    @Test
    fun `a tea without a catalog entry has no overflow menu (SCR-04, D-098)`() {
        show()

        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithContentDescription("Weitere Optionen").assertDoesNotExist()
    }

    @Test
    fun `an alert removes the open overflow menu in its first frame (rule 8, D-098)`() {
        catalog.details.value = mapOf("TG0557" to CatalogTeaDetail(key = "TG0557", name = "Hisui", productUrl = SHOP))
        val waiting = FakeBrewStarter.readyState(tanabata(), "other")
        starter.state.value = waiting.copy(phase = TimerPhase.RUNNING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        // With motion on, a normal close would still be fading after two frames; only the alert gate is instant.
        show(reduceMotion = false)
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Im Katalog ansehen").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        starter.state.value = waiting.copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        // The frame that recomposes, and the frame that drops the menu's window, without its close animation.
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Im Katalog ansehen").assertDoesNotExist()
        assertThat(catalogOpened).isEmpty()
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
    fun `the Vorwarnung row lists each step's pre-alert and opens the Schritte-Sheet too (SCR-04)`() {
        // Hisui's Kyusu recipe, 1:00 · 0:10 · 0:10, with the global 10 s (TMR-23; 09 §4 v0.2.1).
        show()
        compose.onNodeWithTag(SETUP_PRE_ALERT_TAG)
            .performScrollTo()
            .assert(hasText("10 · – · –"))
            .assert(hasContentDescription("10 Sekunden, keine, keine"))

        compose.onNodeWithTag(SETUP_PRE_ALERT_TAG).performClick()

        compose.onNodeWithText("Schritte für diese Sitzung").assertIsDisplayed()
        compose.onNodeWithText("Auto · 10\u00A0s", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the vessel choice offers all nine seed vessels and sets the usable volume (WARE-03, WARE-06)`() {
        show()

        compose.onNodeWithContentDescription("Gefäß: Kyusu 250 ml. Ändern").performScrollTo().performClick()
        compose.waitForIdle()

        // Each seed name ends with its volume: the name alone, never „Kyusu 120 ml · 120 ml"; its volume
        // keeps its unit on the same line.
        listOf(
            "Becher 250\u00A0ml",
            "Becher 300\u00A0ml",
            "Becher 350\u00A0ml",
            "Becher mit Sieb 400\u00A0ml",
            "Kyusu 120\u00A0ml",
            "Kyusu 180\u00A0ml",
            "Kyusu 250\u00A0ml",
            "Kyusu 300\u00A0ml",
            "Kyusu 360\u00A0ml",
        ).forEach { compose.onNode(hasText(it, substring = false) and hasAnyAncestor(isPopup())).assertExists() }

        compose.onNode(hasText("Kyusu 120\u00A0ml", substring = false) and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        compose.onNode(hasText("Wasser") and hasText("120\u00A0ml")).assertExists()
        compose.onNodeWithContentDescription("Gefäß: Kyusu 120 ml. Ändern").assertExists()
        compose.onNodeWithText("Kyusu 120\u00A0ml", substring = false).assertExists()
    }

    @Test
    fun `a vessel whose name doesn't end with its volume shows the volume after it (WARE-03)`() {
        runBlocking {
            val kyusu = SeedData.vessels.first { it.name == "Kyusu 250 ml" }
            collection.vessels.saveVessel(kyusu.copy(name = "Omas Kyusu"))
            collection.vessels.saveVessel(
                SeedData.vessels.first { it.name == "Becher 300 ml" }.copy(name = "Becher 250 ml"),
            )
        }
        show()

        compose.onNodeWithText("Omas Kyusu · 250\u00A0ml").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Gefäß: Omas Kyusu. Ändern").performClick()
        compose.waitForIdle()

        // A name that ends with another volume than the vessel's usable one keeps the volume too.
        compose.onNode(hasText("Becher 250 ml · 300\u00A0ml") and hasAnyAncestor(isPopup())).assertExists()
    }

    @Test
    fun `the Vorwarnung row shows mixed values with the unit once, one value when all are equal (SCR-04)`() {
        var state by mutableStateOf(kyusu.copy(steps = listOf(infusion(60), infusion(20), infusion(10))))
        compose.setContent { UmamiTheme(reduceMotion = true) { BrewSetupScreen(state) } }
        val row = compose.onNodeWithTag(SETUP_PRE_ALERT_TAG).performScrollTo()

        row.assert(hasText("10 · 5 · –")).assert(hasContentDescription("10 Sekunden, 5 Sekunden, keine"))
        state = kyusu.copy(steps = listOf(infusion(60, preAlert = 20), infusion(20), infusion(45)))
        row.assert(hasText("20 · 5 · 10\u00A0s"))
        state = kyusu.copy(steps = listOf(infusion(60), infusion(30), infusion(45)))
        row.assert(hasText("10\u00A0s")).assert(hasContentDescription("10 Sekunden"))
        state = kyusu.copy(steps = listOf(infusion(60, preAlert = 0), infusion(10), infusion(19, preAlert = 15)))
        row.assert(hasText("–")).assert(hasContentDescription("keine"))
        // B = 20 s: 0:45 gives min(20, 15) = 15 s.
        state = kyusu.copy(preAlertBaseSeconds = 20, steps = listOf(infusion(60), infusion(45)))
        row.assert(hasText("20 · 15\u00A0s"))
        // A cold brew has long steps only, so none at all (TMR-18).
        state = kyusu.copy(method = BrewMethod.COLD_BREW, steps = listOf(infusion(300)))
        row.assert(hasText("–"))
    }

    @Test
    fun `the Vorwarnung row gives a hot step of 10 to 59 min its pre-alert, a long step none (TMR-18, D-098)`() {
        var state by mutableStateOf(kyusu.copy(steps = listOf(infusion(15 * 60))))
        compose.setContent { UmamiTheme(reduceMotion = true) { BrewSetupScreen(state) } }
        val row = compose.onNodeWithTag(SETUP_PRE_ALERT_TAG).performScrollTo()

        row.assert(hasText("10 s")).assert(hasContentDescription("10 Sekunden"))
        state = kyusu.copy(steps = listOf(infusion(59 * 60 + 59, preAlert = 30), infusion(60 * 60)))
        row.assert(hasText("30 · –")).assert(hasContentDescription("30 Sekunden, keine"))
        state = kyusu.copy(steps = listOf(infusion(60 * 60, preAlert = 10)))
        row.assert(hasText("–"))
        state = kyusu.copy(method = BrewMethod.COLD_BREW, steps = listOf(infusion(15 * 60)))
        row.assert(hasText("–"))
        state = kyusu.copy(method = BrewMethod.ICE_BREW, steps = listOf(infusion(15 * 60)))
        row.assert(hasText("–"))
    }

    private fun infusion(seconds: Int, preAlert: Int? = null) =
        PlannedStep(StepKind.INFUSION, seconds, 65, preAlertSeconds = preAlert)

    private companion object {
        const val SHOP = "https://example.test/tee"
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
