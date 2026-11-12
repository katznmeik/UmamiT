package com.umamit.core.data

import com.google.common.truth.Truth.assertThat
import com.umamit.core.common.WallClock
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.StepRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Accepting the recipe offer (PRF-07, 05 §3.7 „Accept the recipe offer", D-112, #228) on an in-memory database: the
 * proposal and `times_applied` written in one transaction, a second and a stale accept refused without a change, and
 * the offer derived from the recipe's and the session's stored state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeOffersTest {
    private var now = START
    private val clock = object : WallClock {
        override fun nowMs(): Long = now

        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val data = TestDatabase()
    private val sessions = RoomSessionRepository(data.db, data.settings, clock)
    private val recipes = RoomRecipeRepository(data.db.recipeDao(), clock)
    private val teas = RoomTeaRepository(data.db.teaDao(), clock)
    private val offers = RoomRecipeOffers(data.db, clock)

    @After
    fun tearDown() = data.close()

    /**
     * Tea „t1" with recipe „r1" (1:00, 0:30 with a 10 s pre-alert, at the base 60 °C), saved a day before; the session
     * „s1" of that recipe, its first infusion ended at 0:48, its second poured as planned, completed at [ENDED].
     */
    private suspend fun earlyEndedSession(firstActual: Int = 48, steps: List<RecipeStep> = RECIPE_STEPS) {
        now = START - DAY_MS
        teas.saveTea(tea("t1"))
        recipes.saveRecipe(recipe("r1", "t1", isDefault = true, steps = steps))
        now = START
        sessions.startSession(
            SessionStart(
                "s1",
                snapshotOf().copy(profileId = "r1"),
                listOf(infusion(60), infusion(30, preAlert = 10)),
                10,
                START,
                "Europe/Berlin",
            ),
        )
        val firstEnd = START + firstActual * SECOND_MS
        sessions.recordStep("s1", 0, StepRecord(60, firstActual, 0, 0, START, firstEnd, false, false))
        sessions.recordStep("s1", 1, StepRecord(30, 30, 0, 0, firstEnd, ENDED, false, false))
        now = ENDED
        sessions.completeSession("s1", autoFinished = false)
        now = ENDED + MINUTE_MS
    }

    private suspend fun recipeSteps(): List<RecipeStep> = checkNotNull(recipes.getRecipe("r1")).steps

    private suspend fun timesApplied(): Boolean = checkNotNull(data.db.sessionDao().get("s1")).timesApplied

    @Test
    fun `the proposal of an early-ended infusion is offered`() = runTest {
        earlyEndedSession()

        assertThat(offers.proposal("s1")).containsExactly(
            RecipeStep(StepKind.INFUSION, 50),
            RecipeStep(StepKind.INFUSION, 30, preAlertSeconds = 10),
        ).inOrder()
    }

    @Test
    fun `accept writes the proposal into the recipe and sets times_applied`() = runTest {
        earlyEndedSession()

        assertThat(offers.accept("s1")).isEqualTo(RecipeOfferAccept.ACCEPTED)

        assertThat(recipeSteps()).containsExactly(
            RecipeStep(StepKind.INFUSION, 50),
            RecipeStep(StepKind.INFUSION, 30, preAlertSeconds = 10),
        ).inOrder()
        assertThat(timesApplied()).isTrue()
        assertThat(offers.proposal("s1")).isNull()
        // The recipe's own values stay; only its steps change (*Im Rezept speichern*).
        val stored = checkNotNull(recipes.getRecipe("r1"))
        assertThat(stored.copy(steps = RECIPE_STEPS)).isEqualTo(recipe("r1", "t1", isDefault = true, RECIPE_STEPS))
    }

    @Test
    fun `accept keeps the recipe's notes by position and kind`() = runTest {
        earlyEndedSession(
            steps = listOf(
                RecipeStep(StepKind.INFUSION, 60, note = "sanft"),
                RecipeStep(StepKind.INFUSION, 30, note = "heiß", preAlertSeconds = 10),
            ),
        )

        assertThat(offers.accept("s1")).isEqualTo(RecipeOfferAccept.ACCEPTED)

        assertThat(recipeSteps()).containsExactly(
            RecipeStep(StepKind.INFUSION, 50, note = "sanft"),
            RecipeStep(StepKind.INFUSION, 30, note = "heiß", preAlertSeconds = 10),
        ).inOrder()
    }

    @Test
    fun `a refused accept keeps the notes untouched`() = runTest {
        val withNotes = listOf(RecipeStep(StepKind.INFUSION, 60, note = "sanft"))
        earlyEndedSession(firstActual = 60)
        recipes.replaceSteps("r1", withNotes)

        assertThat(offers.accept("s1")).isEqualTo(RecipeOfferAccept.NOT_APPLICABLE)

        assertThat(recipeSteps()).isEqualTo(withNotes)
    }

    @Test
    fun `a second accept changes nothing`() = runTest {
        earlyEndedSession()
        offers.accept("s1")
        val afterFirst = data.db.recipeDao().get("r1")
        val sessionAfterFirst = data.db.sessionDao().get("s1")
        now += MINUTE_MS

        assertThat(offers.accept("s1")).isEqualTo(RecipeOfferAccept.NOT_APPLICABLE)

        assertThat(data.db.recipeDao().get("r1")).isEqualTo(afterFirst)
        assertThat(data.db.sessionDao().get("s1")).isEqualTo(sessionAfterFirst)
    }

    @Test
    fun `a stale accept after the recipe changed since the session changes nothing`() = runTest {
        earlyEndedSession()
        val edited = listOf(RecipeStep(StepKind.INFUSION, 75))
        recipes.replaceSteps("r1", edited)
        val sessionBefore = data.db.sessionDao().get("s1")

        assertThat(offers.proposal("s1")).isNull()
        assertThat(offers.accept("s1")).isEqualTo(RecipeOfferAccept.NOT_APPLICABLE)

        assertThat(recipeSteps()).isEqualTo(edited)
        assertThat(data.db.sessionDao().get("s1")).isEqualTo(sessionBefore)
    }

    @Test
    fun `a recipe saved during the session still offers`() = runTest {
        earlyEndedSession()
        now = START + 10 * SECOND_MS
        recipes.replaceSteps("r1", listOf(RecipeStep(StepKind.INFUSION, 60), RecipeStep(StepKind.INFUSION, 40)))
        now = ENDED + MINUTE_MS

        assertThat(offers.proposal("s1")).isNotNull()
    }

    @Test
    fun `a session poured as planned offers nothing`() = runTest {
        earlyEndedSession(firstActual = 60)

        assertThat(offers.proposal("s1")).isNull()
        assertThat(offers.accept("s1")).isEqualTo(RecipeOfferAccept.NOT_APPLICABLE)
        assertThat(recipeSteps()).isEqualTo(RECIPE_STEPS)
    }

    @Test
    fun `an archived tea, a deleted recipe or a deleted tea offer nothing`() = runTest {
        earlyEndedSession()
        data.db.teaDao().setArchived("t1", true, now)
        assertThat(offers.proposal("s1")).isNull()
        assertThat(offers.accept("s1")).isEqualTo(RecipeOfferAccept.NOT_APPLICABLE)
        assertThat(timesApplied()).isFalse()

        data.db.teaDao().setArchived("t1", false, now)
        assertThat(offers.proposal("s1")).isNotNull()

        teas.deleteTea("t1")
        assertThat(offers.proposal("s1")).isNull()
        assertThat(offers.accept("s1")).isEqualTo(RecipeOfferAccept.NOT_APPLICABLE)
    }

    @Test
    fun `an unknown session offers nothing`() = runTest {
        assertThat(offers.proposal("none")).isNull()
        assertThat(offers.accept("none")).isEqualTo(RecipeOfferAccept.NOT_APPLICABLE)
    }

    private companion object {
        const val START = 1_800_000_000_000L
        const val SECOND_MS = 1_000L
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 86_400_000L
        const val ENDED = START + 120 * SECOND_MS

        val RECIPE_STEPS =
            listOf(RecipeStep(StepKind.INFUSION, 60), RecipeStep(StepKind.INFUSION, 30, preAlertSeconds = 10))
    }
}
