package com.umamit.feature.brew.steps

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.StepStatus
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
        click("Vorwarnung: Wie Einstellung. Ändern")
        compose.onNodeWithText("Aus").performClick()
        compose.onNodeWithText("Fertig").performClick()

        val after = state.send(requireNotNull(done)) as TimerState.Running
        assertThat(after.session.steps.take(2)).isEqualTo(before.session.steps.take(2))
        assertThat(after.deadlineElapsedMs).isEqualTo(before.deadlineElapsedMs)
        assertThat(after.session.steps.drop(2).map { it.planned }).containsExactly(
            PlannedStep(StepKind.INFUSION, 35, 65),
            PlannedStep(StepKind.INFUSION, 45, 55),
            PlannedStep(StepKind.INFUSION, 70, 55),
        ).inOrder()
        assertThat(after.session.steps.drop(2).map { it.status }).containsExactly(
            StepStatus.PENDING,
            StepStatus.PENDING,
            StepStatus.PENDING,
        )
        assertThat(after.session.preAlert.effectiveSeconds).isEqualTo(0)
    }

    @Test
    fun `the save request persisted through the recipe repository reads back identically`() = runTest {
        teas.saveTea(Tea(id = "t1", name = "Bio Sencha Hisui", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        recipes.saveRecipe(recipe(preAlertSeconds = 5))
        val recipe = requireNotNull(recipes.getRecipe("r1"))
        show(input(recipe, PreAlertChoice.Seconds(20)))

        click("Ziehzeit verlängern, Schritt 2")
        click("Temperatur erhöhen, Schritt 2")
        compose.onNodeWithText("Aufguss hinzufügen").performScrollTo().performClick()
        compose.onNodeWithText("Im Rezept speichern").performClick()
        val request = requireNotNull(saved)
        recipes.replaceStepsAndPreAlert(
            recipe.id,
            request.recipeSteps,
            request.recipePreAlertSeconds(recipe.preAlertSeconds),
        )

        val stored = requireNotNull(recipes.getRecipe("r1"))
        assertThat(stored.steps).isEqualTo(request.recipeSteps)
        assertThat(stored.steps).containsExactly(
            RecipeStep(StepKind.INFUSION, 90, 70),
            RecipeStep(StepKind.INFUSION, 35, 65),
            RecipeStep(StepKind.INFUSION, 55, 65),
        ).inOrder()
        assertThat(stored.preAlertSeconds).isEqualTo(20)
        assertThat(stored.copy(steps = recipe.steps, preAlertSeconds = 5)).isEqualTo(recipe)
    }

    @Test
    fun `Wie Einstellung keeps the recipe's own pre-alert`() = runTest {
        teas.saveTea(Tea(id = "t1", name = "Bio Sencha Hisui", category = TeaCategory.GREEN, form = TeaForm.LOOSE_LEAF))
        recipes.saveRecipe(recipe(preAlertSeconds = 15))
        val recipe = requireNotNull(recipes.getRecipe("r1"))
        show(input(recipe, PreAlertChoice.Inherit))

        compose.onNodeWithText("Im Rezept speichern").performClick()
        val request = requireNotNull(saved)
        recipes.replaceStepsAndPreAlert(
            recipe.id,
            request.recipeSteps,
            request.recipePreAlertSeconds(recipe.preAlertSeconds),
        )

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

    /** The sheet for a session started from [recipe], first infusion done. */
    private fun input(recipe: Recipe, preAlert: PreAlertChoice) = StepsSheetInput(
        steps = recipe.steps.mapIndexed { index, step ->
            StepsSheetStep(
                kind = step.kind,
                plannedSeconds = step.durationSeconds,
                tempC = step.tempC,
                status = if (index == 0) StepStatus.DONE else StepStatus.PENDING,
            )
        },
        preAlert = preAlert,
        hasRecipe = true,
        baseTempC = recipe.baseTempC,
    )

    private companion object {
        const val START_WALL_MS = 1_792_000_000_000L
    }
}
