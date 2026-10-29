package com.umamit.feature.brew.steps

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.ClockReading
import com.umamit.core.common.WallClock
import com.umamit.core.data.RoomRecipeRepository
import com.umamit.core.data.RoomTeaRepository
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEffect
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerState
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Wiring proof (#38): the sheet's requests are accepted by the engine and the
 * recipe repository unchanged, so SCR-04 and SCR-05 only have to forward them
 * (TMR-11, BRW-02, D-048).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class StepsSheetIntegrationTest {

    @get:Rule
    val compose = createComposeRule()

    private val db: UmamiDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UmamiDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val wallClock = object : WallClock {
        override fun nowMs(): Long = START_WALL_MS

        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val teas = RoomTeaRepository(db.teaDao(), wallClock)
    private val recipes = RoomRecipeRepository(db.recipeDao(), wallClock)

    private var elapsedMs = 1_000_000L
    private var wallMs = START_WALL_MS

    @After
    fun tearDown() = db.close()

    private fun now() = ClockReading(elapsedMs, wallMs, ZoneOffset.UTC, bootCount = 1)

    private fun advanceSeconds(seconds: Int) {
        elapsedMs += seconds * 1_000L
        wallMs += seconds * 1_000L
    }

    private fun TimerState.send(event: TimerEvent): TimerState {
        val transition = BrewTimerEngine.reduce(this, event, now())
        assertThat(transition.outcome).isEqualTo(TransitionOutcome.Accepted)
        return transition.state
    }

    private var done: TimerEvent.EditSteps? = null
    private var saved: SaveToRecipeRequest? = null

    private fun show(input: StepsSheetInput) {
        val state = StepsSheetState(input)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                StepsSheetContent(state = state, onDone = { done = it }, onSaveToRecipe = { saved = it })
            }
        }
    }

    private fun click(description: String) =
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()

    @Test
    fun `the engine accepts the Fertig request and changes only the pending steps`() {
        // Rinse done, infusion 1 running, infusions 2 and 3 pending.
        var state: TimerState = TimerState.Idle.send(
            TimerEvent.StartSession(
                sessionId = "s1",
                steps = listOf(
                    PlannedStep(StepKind.RINSE, 10, 80),
                    PlannedStep(StepKind.INFUSION, 60, 60),
                    PlannedStep(StepKind.INFUSION, 30, 65),
                    PlannedStep(StepKind.INFUSION, 45, null),
                ),
                preAlert = PreAlertSettings.inherited(recipeSeconds = 10, globalSeconds = 10),
                longStepsOnly = false,
            ),
        )
        state = state.send(TimerEvent.Start)
        advanceSeconds(10)
        state = state.send(TimerEvent.EndStepNow)
        state = state.send(TimerEvent.Start)
        advanceSeconds(20)
        val before = state as TimerState.Running
        show(StepsSheetInput.of(before.session, hasRecipe = true, baseTempC = 60))

        click("Ziehzeit verlängern, Schritt 3")
        click("Temperatur senken, Schritt 4")
        compose.onNodeWithText("Aufguss hinzufügen").performScrollTo().performClick()
        choosePreAlert(2, "Automatisch, 10 Sekunden vorher", "Aus")
        choosePreAlert(4, "Automatisch, 10 Sekunden vorher", "15 s")
        compose.onNodeWithText("Fertig").performClick()

        val after = state.send(requireNotNull(done)) as TimerState.Running
        // The done rinse stays as it was; the running infusion changes only its pre-alert (SCR-31, D-093).
        assertThat(after.session.steps[0]).isEqualTo(before.session.steps[0])
        assertThat(after.session.steps[1]).isEqualTo(before.session.steps[1].copy(preAlertSeconds = 0))
        assertThat(after.deadlineElapsedMs).isEqualTo(before.deadlineElapsedMs)
        assertThat(after.session.steps.drop(2).map { it.planned }).containsExactly(
            PlannedStep(StepKind.INFUSION, 35, 65),
            PlannedStep(StepKind.INFUSION, 45, 55, preAlertSeconds = 15),
            PlannedStep(StepKind.INFUSION, 70, 55),
        ).inOrder()
        assertThat(after.session.steps.drop(2).map { it.status }).containsExactly(
            StepStatus.PENDING,
            StepStatus.PENDING,
            StepStatus.PENDING,
        )
        assertThat(after.session.preAlert).isEqualTo(before.session.preAlert)
        assertThat(BrewTimerEngine.dueTimes(after).preAlertAtElapsedMs).isNull()
    }

    @Test
    fun `a running step's Vorwarnung changed in the sheet moves its tick at once, the alert stays on time`() {
        // TMR-23, SCR-31, rule 8 (#92): 1:00 with B = 10 s, 20 s in; the sheet sets 20 s, so 20 s before the end.
        var state = runningFirstOf(PlannedStep(StepKind.INFUSION, 60, 60), PlannedStep(StepKind.INFUSION, 20, 60))
        val deadline = (state as TimerState.Running).deadlineElapsedMs
        assertThat(BrewTimerEngine.dueTimes(state).preAlertAtElapsedMs).isEqualTo(deadline - 10_000)
        show(StepsSheetInput.of(state.session, hasRecipe = false, baseTempC = 60))
        choosePreAlert(1, "Automatisch, 10 Sekunden vorher", "20 s")
        compose.onNodeWithText("Fertig").performClick()

        val edit = BrewTimerEngine.reduce(state, requireNotNull(done), now())

        assertThat(edit.outcome).isEqualTo(TransitionOutcome.Accepted)
        state = edit.state
        val due = BrewTimerEngine.dueTimes(state)
        assertThat(due.preAlertAtElapsedMs).isEqualTo(deadline - 20_000)
        assertThat(due.deadlineElapsedMs).isEqualTo(deadline)
        assertThat(edit.effects).contains(TimerEffect.ScheduleAlarms(due))
        assertThat(BrewTimerEngine.preAlertLeadSeconds(state)).isEqualTo(20)
        // The tick plays at its new time, the alert at the unchanged deadline.
        advanceSeconds(19)
        assertThat(BrewTimerEngine.dueEvents(state, now())).isEmpty()
        advanceSeconds(1)
        assertThat(BrewTimerEngine.dueEvents(state, now())).containsExactly(TimerEvent.PreAlertReached)
        val tick = BrewTimerEngine.reduce(state, TimerEvent.PreAlertReached, now())
        assertThat(tick.effects).contains(TimerEffect.PlayPreAlert)
        state = tick.state
        advanceSeconds(20)
        assertThat(BrewTimerEngine.dueEvents(state, now())).containsExactly(TimerEvent.DeadlineReached)
        assertThat(BrewTimerEngine.reduce(state, TimerEvent.DeadlineReached, now()).effects)
            .contains(TimerEffect.StartAlert)
    }

    @Test
    fun `Aus for the running step in the sheet cancels only its tick, never its alert`() {
        val state = runningFirstOf(PlannedStep(StepKind.INFUSION, 60, 60))
        val deadline = (state as TimerState.Running).deadlineElapsedMs
        show(StepsSheetInput.of(state.session, hasRecipe = false, baseTempC = 60))
        choosePreAlert(1, "Automatisch, 10 Sekunden vorher", "Aus")
        compose.onNodeWithText("Fertig").performClick()

        val edit = BrewTimerEngine.reduce(state, requireNotNull(done), now())

        assertThat(edit.outcome).isEqualTo(TransitionOutcome.Accepted)
        assertThat(edit.effects).contains(TimerEffect.ScheduleAlarms(DueTimes(deadlineElapsedMs = deadline)))
        advanceSeconds(40)
        assertThat(BrewTimerEngine.dueEvents(edit.state, now())).containsExactly(TimerEvent.DeadlineReached)
    }

    @Test
    fun `the save request persisted through the recipe repository reads back identically`() = runTest {
        teas.saveTea(Tea(id = "t1", name = "Bio Sencha Hisui", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        recipes.saveRecipe(recipe(preAlertSeconds = 5))
        val recipe = requireNotNull(recipes.getRecipe("r1"))
        show(input(recipe))

        click("Ziehzeit verlängern, Schritt 2")
        click("Temperatur erhöhen, Schritt 2")
        compose.onNodeWithText("Aufguss hinzufügen").performScrollTo().performClick()
        // B = the recipe's 5 s: 0:35 shows „Auto · 5 s"; each step gets its own value (D-093).
        choosePreAlert(2, "Automatisch, 5 Sekunden vorher", "20 s")
        choosePreAlert(3, "Automatisch, 5 Sekunden vorher", "Aus")
        compose.onNodeWithText("Im Rezept speichern").performClick()
        val request = requireNotNull(saved)
        recipes.replaceSteps(recipe.id, request.recipeSteps)

        val stored = requireNotNull(recipes.getRecipe("r1"))
        assertThat(stored.steps).isEqualTo(request.recipeSteps)
        // Each step keeps its own pre-alert; the done one keeps its own too.
        assertThat(stored.steps).containsExactly(
            RecipeStep(StepKind.INFUSION, 90, 70),
            RecipeStep(StepKind.INFUSION, 35, 65, preAlertSeconds = 20),
            RecipeStep(StepKind.INFUSION, 55, 65, preAlertSeconds = 0),
        ).inOrder()
        // The recipe's base B is not a step field and stays.
        assertThat(stored.copy(steps = recipe.steps)).isEqualTo(recipe)
    }

    @Test
    fun `Automatisch keeps the steps without a value of their own and the recipe's own base`() = runTest {
        teas.saveTea(Tea(id = "t1", name = "Bio Sencha Hisui", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        recipes.saveRecipe(recipe(preAlertSeconds = 15))
        val recipe = requireNotNull(recipes.getRecipe("r1"))
        show(input(recipe))

        compose.onNodeWithText("Im Rezept speichern").performClick()
        val request = requireNotNull(saved)
        recipes.replaceSteps(recipe.id, request.recipeSteps)

        assertThat(recipes.getRecipe("r1")).isEqualTo(recipe)
    }

    private fun recipe(preAlertSeconds: Int?) = Recipe(
        id = "r1",
        teaId = "t1",
        name = "Kyusu",
        method = BrewMethod.KYUSU,
        leafGrams = 4.0,
        waterMl = 100,
        baseTempC = 60,
        steps = listOf(RecipeStep(StepKind.INFUSION, 90, 70), RecipeStep(StepKind.INFUSION, 30)),
        preAlertSeconds = preAlertSeconds,
        isDefault = true,
    )

    /** A session of [steps] with B = 10 s whose first step has run for 20 s. */
    private fun runningFirstOf(vararg steps: PlannedStep): TimerState {
        val ready = TimerState.Idle.send(
            TimerEvent.StartSession("s1", steps.toList(), PreAlertSettings(inheritedSeconds = 10), false),
        )
        val running = ready.send(TimerEvent.Start)
        advanceSeconds(20)
        return running
    }

    /** Picks [option] in step [number]'s pre-alert menu, opened through its TalkBack click action. */
    private fun choosePreAlert(number: Int, spoken: String, option: String) {
        compose.onNodeWithContentDescription("Vorwarnung, Schritt $number: $spoken. Ändern")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(option).performClick()
    }

    /** The sheet for a session started from [recipe], first infusion done. */
    private fun input(recipe: Recipe) = StepsSheetInput(
        steps = recipe.steps.mapIndexed { index, step ->
            StepsSheetStep(
                kind = step.kind,
                plannedSeconds = step.durationSeconds,
                tempC = step.tempC,
                status = if (index == 0) StepStatus.DONE else StepStatus.PENDING,
                preAlertSeconds = step.preAlertSeconds,
            )
        },
        hasRecipe = true,
        baseTempC = recipe.baseTempC,
        preAlertBaseSeconds = recipe.preAlertSeconds ?: PreAlertSettings.DEFAULT_GLOBAL_SECONDS,
    )

    private companion object {
        const val START_WALL_MS = 1_792_000_000_000L
    }
}
