package com.umamit.feature.brew.setup

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.SeedData
import com.umamit.core.data.SessionStart
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.KettleWater
import com.umamit.core.model.StepKind
import com.umamit.core.model.UserSettings
import com.umamit.core.testing.FakeBrewStarter
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.testing.FakeSettingsRepository
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.steps.SaveToRecipeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The setup's view model (SCR-04; BRW-02, BRW-03, BRW-10, TMR-01, TMR-21) on
 * the seed teas of 08 §2.5a, with fake repositories and a fake controller.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewSetupViewModelTest {
    private val collection = FakeCollection()
    private val settings = FakeSettingsRepository(UserSettings(preAlertSeconds = 10))
    private val sessions = FakeSessions(collection, settings)
    private val starter = FakeBrewStarter()

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SeedData.teas.forEach { collection.teas.saveTea(it) }
        SeedData.vessels.forEach { collection.vessels.saveVessel(it) }
        SeedData.recipes.forEach { collection.recipes.saveRecipe(it) }
        SeedData.initialStock(0).forEach { collection.stock.addEvent(it) }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(teaId: String? = SeedData.HISUI_TEA_ID, sessionId: String? = null) = BrewSetupViewModel(
        SavedStateHandle(mapOf("teaId" to teaId, "sessionId" to sessionId)),
        collection.teas,
        collection.recipes,
        collection.vessels,
        collection.stock,
        sessions.sessions,
        settings,
        starter,
    )

    private val BrewSetupViewModel.ready get() = state.value as BrewSetupUiState.Ready

    private fun BrewSetupUiState.Ready.recipe(name: String) = recipes.first { it.name == name }.id

    private fun BrewSetupUiState.Ready.vessel(name: String) = vessels.first { it.name == name }.id

    @Test
    fun `the setup opens with the tea's default recipe and its vessel`() {
        val ready = viewModel().ready

        assertThat(ready.teaName).isEqualTo("Bio Sencha Hisui")
        assertThat(ready.recipes.map { it.name }).containsExactly("Kyusu", "Westlich", "Kaltaufguss").inOrder()
        assertThat(ready.selectedRecipeId).isEqualTo(ready.recipe("Kyusu"))
        assertThat(ready.selectedVesselId).isEqualTo(SeedData.KYUSU_VESSEL_ID)
        assertThat(ready.leafGrams).isEqualTo(5.0)
        assertThat(ready.waterMl).isEqualTo(250)
        assertThat(ready.ratioLocked).isTrue()
        assertThat(ready.steps).containsExactly(
            PlannedStep(StepKind.INFUSION, 60, 65),
            PlannedStep(StepKind.INFUSION, 10, 65),
            PlannedStep(StepKind.INFUSION, 10, 65),
        ).inOrder()
        assertThat(ready.kettle).isEqualTo(Kettle(65, KettleWater.HOT))
        assertThat(ready.stockGrams).isEqualTo(100.0)
        assertThat(ready.canSaveToRecipe).isFalse()
    }

    @Test
    fun `the 400 ml Becher scales the Kyusu recipe to 8,0 g and back to 5,0 g - times and temperatures stay`() {
        val vm = viewModel()
        val steps = vm.ready.steps

        vm.selectVessel(vm.ready.vessel("Becher mit Sieb"))
        assertThat(vm.ready.waterMl).isEqualTo(400)
        assertThat(vm.ready.leafGrams).isEqualTo(8.0)
        assertThat(vm.ready.steps).isEqualTo(steps)
        assertThat(vm.ready.kettle.tempC).isEqualTo(65)

        vm.selectVessel(vm.ready.vessel("Kyusu"))
        assertThat(vm.ready.waterMl).isEqualTo(250)
        assertThat(vm.ready.leafGrams).isEqualTo(5.0)
    }

    @Test
    fun `the Westlich recipe of Tanabata in the 250 ml Kyusu takes 2,5 g at 70 and 75 degrees`() {
        val vm = viewModel(SeedData.TANABATA_TEA_ID)

        vm.selectRecipe(vm.ready.recipe("Westlich"))
        assertThat(vm.ready.leafGrams).isEqualTo(4.0)
        assertThat(vm.ready.waterMl).isEqualTo(400)

        vm.selectVessel(vm.ready.vessel("Kyusu"))
        assertThat(vm.ready.leafGrams).isEqualTo(2.5)
        assertThat(vm.ready.steps).containsExactly(
            PlannedStep(StepKind.INFUSION, 60, 70),
            PlannedStep(StepKind.INFUSION, 15, 75),
        ).inOrder()
    }

    @Test
    fun `with the ratio lock the steppers rescale the other value - without it they do not`() {
        val vm = viewModel()

        vm.stepWater(up = true)
        assertThat(vm.ready.waterMl).isEqualTo(255)
        assertThat(vm.ready.leafGrams).isEqualTo(5.1)

        vm.stepLeaf(up = true)
        assertThat(vm.ready.leafGrams).isEqualTo(5.2)
        assertThat(vm.ready.waterMl).isEqualTo(260)

        vm.setRatioLocked(false)
        vm.stepLeaf(up = true)
        assertThat(vm.ready.leafGrams).isEqualTo(5.3)
        assertThat(vm.ready.waterMl).isEqualTo(260)
        assertThat(vm.ready.ratioPer100Ml).isEqualTo(2.0)
    }

    @Test
    fun `Im Rezept speichern appears only after a change and stores the change`() = runTest {
        val vm = viewModel()
        assertThat(vm.ready.canSaveToRecipe).isFalse()

        vm.selectVessel(vm.ready.vessel("Becher mit Sieb"))
        assertThat(vm.ready.canSaveToRecipe).isTrue()

        vm.saveToRecipe()

        val saved = collection.recipes.getRecipe(vm.ready.selectedRecipeId!!)!!
        assertThat(saved.vesselId).isEqualTo(SeedData.MUG_VESSEL_ID)
        assertThat(saved.waterMl).isEqualTo(400)
        assertThat(saved.leafGrams).isEqualTo(8.0)
        assertThat(vm.ready.canSaveToRecipe).isFalse()
    }

    @Test
    fun `undoing a change by hand hides Im Rezept speichern again`() {
        val vm = viewModel()

        vm.stepWater(up = true)
        vm.stepWater(up = false)

        assertThat(vm.ready.canSaveToRecipe).isFalse()
    }

    @Test
    fun `the Schritte-Sheet's steps and pre-alerts apply to this session - its save writes them into the recipe`() =
        runTest {
            val vm = viewModel()
            assertThat(vm.ready.effectivePreAlertSeconds).isEqualTo(10)
            assertThat(vm.ready.preAlert).isEqualTo(PreAlertChoice.Inherit)
            val steps = listOf(
                PlannedStep(StepKind.INFUSION, 90, 70, preAlertSeconds = 20),
                PlannedStep(StepKind.INFUSION, 30, 70, preAlertSeconds = 20),
            )

            vm.applySteps(TimerEvent.EditSteps(steps))
            assertThat(vm.ready.steps).containsExactlyElementsIn(steps).inOrder()
            assertThat(vm.ready.preAlert).isEqualTo(PreAlertChoice.Seconds(20))
            assertThat(vm.ready.effectivePreAlertSeconds).isEqualTo(20)
            assertThat(vm.ready.canSaveToRecipe).isTrue()

            vm.saveStepsToRecipe(SaveToRecipeRequest(vm.ready.steps))
            val saved = collection.recipes.getRecipe(vm.ready.selectedRecipeId!!)!!
            assertThat(saved.steps.map { it.durationSeconds to it.preAlertSeconds })
                .containsExactly(90 to 20, 30 to 20).inOrder()
            // The recipe's base B stays; each step carries its own (TMR-23, D-093).
            assertThat(saved.preAlertSeconds).isNull()
            assertThat(viewModel().ready.steps.map { it.preAlertSeconds }).containsExactly(20, 20)
        }

    @Test
    fun `Bruehen starten hands each step's own pre-alert to the controller (TMR-23)`() = runTest {
        val vm = viewModel()
        vm.applySteps(
            TimerEvent.EditSteps(
                listOf(
                    PlannedStep(StepKind.INFUSION, 60, 65, preAlertSeconds = 0),
                    PlannedStep(StepKind.INFUSION, 20, 65),
                ),
            ),
        )

        vm.startBrew()

        assertThat(starter.starts.single().steps.map { it.preAlertSeconds }).containsExactly(0, null).inOrder()
    }

    @Test
    fun `cold brew shows Kaltes Wasser at 5 degrees (BRW-10)`() {
        val vm = viewModel()

        vm.selectRecipe(vm.ready.recipe("Kaltaufguss"))

        assertThat(vm.ready.kettle).isEqualTo(Kettle(5, KettleWater.COLD))
        assertThat(vm.ready.method).isEqualTo(BrewMethod.COLD_BREW)
    }

    @Test
    fun `Bruehen starten hands the controller the snapshot and the plan, then opens the timer (TMR-01)`() = runTest {
        val vm = viewModel()
        vm.selectVessel(vm.ready.vessel("Becher mit Sieb"))

        vm.startBrew()

        val start: BrewStart = starter.starts.single()
        assertThat(start.snapshot.teaId).isEqualTo(SeedData.HISUI_TEA_ID)
        assertThat(start.snapshot.teaName).isEqualTo("Bio Sencha Hisui")
        assertThat(start.snapshot.profileName).isEqualTo("Kyusu")
        assertThat(start.snapshot.vesselName).isEqualTo("Becher mit Sieb")
        assertThat(start.snapshot.leafGrams).isEqualTo(8.0)
        assertThat(start.snapshot.waterMl).isEqualTo(400)
        assertThat(start.snapshot.baseTempC).isEqualTo(65)
        assertThat(start.snapshot.method).isEqualTo(BrewMethod.KYUSU)
        assertThat(start.steps.map { it.plannedSeconds }).containsExactly(60, 10, 10).inOrder()
        assertThat(start.recipePreAlertSeconds).isNull()
        assertThat(vm.events.first()).isEqualTo(BrewSetupEvent.OpenTimer)
    }

    @Test
    fun `a second session asks first, and Beenden und neu starten replaces it (TMR-21)`() = runTest {
        starter.state.value = FakeBrewStarter.readyState(brewAgain("Bio Sencha Tanabata"), "other")
        val vm = viewModel()

        vm.startBrew()
        assertThat(vm.ready.question).isEqualTo(ActiveSessionQuestion(teaName = "Bio Sencha Tanabata"))
        assertThat(starter.starts).isEmpty()

        vm.confirmRestart()

        assertThat(vm.ready.question).isNull()
        assertThat(starter.replaced).isEqualTo(1)
        assertThat(starter.starts.single().snapshot.teaName).isEqualTo("Bio Sencha Hisui")
        assertThat(vm.events.first()).isEqualTo(BrewSetupEvent.OpenTimer)
    }

    @Test
    fun `Abbrechen keeps the active session`() = runTest {
        starter.state.value = FakeBrewStarter.readyState(brewAgain("Bio Sencha Tanabata"), "other")
        val vm = viewModel()

        vm.startBrew()
        vm.dismissQuestion()

        assertThat(vm.ready.question).isNull()
        assertThat(starter.starts).isEmpty()
    }

    @Test
    fun `an alert closes the question (rule 8)`() = runTest {
        val waiting = FakeBrewStarter.readyState(brewAgain("Bio Sencha Tanabata"), "other")
        starter.state.value = waiting
        val vm = viewModel()
        vm.startBrew()

        starter.state.value = waiting.copy(
            phase = com.umamit.core.model.TimerPhase.ALERTING,
            deadlineElapsedMs = 1,
            deadlineWallMs = 1,
        )

        assertThat(vm.ready.question).isNull()
    }

    @Test
    fun `Bruehen anpassen opens the waiting session's values and starts over without asking`() = runTest {
        val request = brewAgain("Bio Sencha Hisui").copy(
            steps = listOf(PlannedStep(StepKind.INFUSION, 75, 70)),
        )
        sessions.sessions.startSession(
            SessionStart("waiting", request.snapshot, request.steps, preAlertSeconds = 10, 0, "Europe/Berlin"),
        )
        starter.state.value = FakeBrewStarter.readyState(request, "waiting")
        val vm = viewModel(teaId = null, sessionId = "waiting")

        assertThat(vm.ready.teaName).isEqualTo("Bio Sencha Hisui")
        assertThat(vm.ready.steps).containsExactly(PlannedStep(StepKind.INFUSION, 75, 70))
        assertThat(vm.ready.waterMl).isEqualTo(250)

        vm.startBrew()

        assertThat(vm.ready.question).isNull()
        assertThat(starter.replaced).isEqualTo(1)
        assertThat(vm.events.first()).isEqualTo(BrewSetupEvent.OpenTimer)
    }

    @Test
    fun `an unknown tea shows the not-found state`() {
        assertThat(viewModel(teaId = "gone").state.value).isEqualTo(BrewSetupUiState.NotFound)
    }

    /** A start of the Kyusu recipe of the seed tea [teaName]. */
    private fun brewAgain(teaName: String) = BrewStart(
        snapshot = com.umamit.core.model.SessionSnapshot(
            teaId = if (teaName.endsWith("Hisui")) SeedData.HISUI_TEA_ID else SeedData.TANABATA_TEA_ID,
            teaName = teaName,
            teaSecondaryName = null,
            teaType = "Sencha",
            styleKey = "sencha",
            category = null,
            profileId = null,
            profileName = "Kyusu",
            method = BrewMethod.KYUSU,
            vesselId = SeedData.KYUSU_VESSEL_ID,
            vesselName = "Kyusu",
            leafGrams = 5.0,
            waterMl = 250,
            baseTempC = 65,
        ),
        steps = listOf(PlannedStep(StepKind.INFUSION, 60, 65)),
        recipePreAlertSeconds = null,
    )
}
