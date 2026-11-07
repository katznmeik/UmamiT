package com.umamit.feature.brew.setup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.engine.PlannedStep
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The setup's *Gefäß* list in v0.4.0 (SCR-04, WARE-02, WARE-03, D-100): *Neues Gefäß …* at its end asks the
 * host for the vessel editor, the vessel saved there comes back selected and scaled like any vessel, and a
 * recipe's archived vessel reads „(archiviert)" without being offered. The list gives way to an alert (rule 8).
 * The real view model on fake repositories; invented vessels (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class BrewSetupVesselTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessions(collection, settings)
    private val starter = FakeBrewStarter()
    private val catalog = FakeCatalogRepository()
    private var newVesselRequests = 0
    private var newVesselId by mutableStateOf<String?>(null)
    private var newVesselConsumed = 0

    @Before
    fun seed() = runBlocking {
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        SeedData.vessels.forEach { collection.vessels.saveVessel(it) }
        SeedData.recipes.forEach { collection.recipes.saveRecipe(it) }
    }

    /** Hisui's setup: its Kyusu recipe brews 5,0 g in 250 ml in „Kyusu 250 ml". */
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
                    onOpenTimer = {},
                    onNewVessel = { newVesselRequests++ },
                    newVesselId = newVesselId,
                    onConsumeNewVessel = {
                        newVesselConsumed++
                        newVesselId = null
                    },
                    viewModel = viewModel,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun openList(vessel: String = "Kyusu 250 ml") {
        compose.onNodeWithContentDescription("Gefäß: $vessel. Ändern").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun inList(text: String) = hasText(text, substring = false) and hasAnyAncestor(isPopup())

    @Test
    fun `Neues Gefaess at the end of the list asks for the vessel editor (WARE-02, D-100)`() {
        show()
        openList()

        val last = compose.onNode(inList("Kyusu 360 ml")).fetchSemanticsNode().boundsInRoot
        val add = compose.onNode(inList("Neues Gefäß …")).performScrollTo().fetchSemanticsNode().boundsInRoot
        assertThat(add.top).isAtLeast(last.bottom)

        compose.onNode(inList("Neues Gefäß …")).performClick()
        compose.waitForIdle()

        assertThat(newVesselRequests).isEqualTo(1)
        compose.onNodeWithText("Neues Gefäß …").assertDoesNotExist()
        // Nothing changed yet: ✕ in the editor returns to this setup.
        compose.onNodeWithContentDescription("Gefäß: Kyusu 250 ml. Ändern").assertExists()
    }

    @Test
    fun `the vessel saved in the editor comes back selected, water and leaf scaled (WARE-02, WARE-03)`() {
        show()
        // *Speichern* in the editor stored it; the host hands its id back.
        runBlocking { collection.vessels.saveVessel(Vessel("omas", "Omas Kyusu", VesselType.KYUSU, 180, 150)) }

        newVesselId = "omas"
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Gefäß: Omas Kyusu. Ändern").assertExists()
        compose.onNode(hasText("Wasser") and hasText("150 ml")).assertExists()
        // 5,0 g / 250 ml, locked: 3,0 g in 150 ml.
        compose.onNode(hasText("Blätter") and hasText("3,0 g")).assertExists()
        assertThat(newVesselConsumed).isEqualTo(1)
    }

    @Test
    fun `a recipe's archived vessel reads archiviert with water and leaf as stored and isn't offered (WARE-02)`() {
        runBlocking {
            val kyusu = SeedData.vessels.first { it.id == SeedData.KYUSU_VESSEL_ID }
            collection.vessels.saveVessel(kyusu.copy(isArchived = true))
        }
        show()

        compose.onNodeWithText("Kyusu 250 ml (archiviert)").assertExists()
        compose.onNode(hasText("Wasser") and hasText("250 ml")).assertExists()
        compose.onNode(hasText("Blätter") and hasText("5,0 g")).assertExists()

        openList("Kyusu 250 ml (archiviert)")
        compose.onNode(inList("Kyusu 250 ml")).assertDoesNotExist()
        compose.onNode(inList("Kyusu 120 ml")).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Gefäß: Kyusu 120 ml. Ändern").assertExists()
        compose.onNodeWithText("(archiviert)", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an alert removes the open vessel list in its first frame (rule 8)`() {
        val waiting = FakeBrewStarter.readyState(tanabata(), "other")
        starter.state.value = waiting.copy(phase = TimerPhase.RUNNING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        show(reduceMotion = false)
        openList()
        compose.onNode(inList("Neues Gefäß …")).assertExists()

        compose.mainClock.autoAdvance = false
        starter.state.value = waiting.copy(phase = TimerPhase.ALERTING, deadlineElapsedMs = 1, deadlineWallMs = 1)
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Neues Gefäß …").assertDoesNotExist()
        assertThat(newVesselRequests).isEqualTo(0)
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
