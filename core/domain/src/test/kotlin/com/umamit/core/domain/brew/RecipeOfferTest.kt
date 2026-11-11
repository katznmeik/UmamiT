package com.umamit.core.domain.brew

import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewSession
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow
import com.umamit.core.model.StepKind
import org.junit.Test

/** The recipe offer's rule (PRF-07 AC1, AC2, AC4; D-112), on invented sessions. */
class RecipeOfferTest {

    @Test
    fun `AC1 a session brewed as planned and poured 20 s late shows no offer`() {
        val session = session(poured(0, planned = 60, actual = 80, overtime = 20), poured(1, planned = 30, actual = 30))

        assertThat(RecipeOffer.of(input(session))).isNull()
    }

    @Test
    fun `AC2 a first infusion ended at 0_48 of 1_00 offers 0_50 for that step`() {
        val session = session(poured(0, planned = 60, actual = 48), poured(1, planned = 30, actual = 30))

        assertThat(RecipeOffer.of(input(session))).containsExactly(
            RecipeStep(StepKind.INFUSION, 50),
            RecipeStep(StepKind.INFUSION, 30),
        ).inOrder()
    }

    @Test
    fun `an early end that rounds back to the set time offers nothing`() {
        val session = session(poured(0, planned = 60, actual = 58), poured(1, planned = 30, actual = 30))

        assertThat(RecipeOffer.of(input(session))).isNull()
    }

    @Test
    fun `a step edited in the Schritte-Sheet is offered with its new time, temperature and pre-alert`() {
        val edited = poured(1, planned = 45, actual = 45).copy(tempC = 65, preAlertSeconds = 0)
        val session = session(poured(0, planned = 60, actual = 60), edited)

        assertThat(RecipeOffer.of(input(session))).containsExactly(
            RecipeStep(StepKind.INFUSION, 60),
            RecipeStep(StepKind.INFUSION, 45, tempC = 65, preAlertSeconds = 0),
        ).inOrder()
    }

    @Test
    fun `an added step is offered at the end`() {
        val session =
            session(poured(0, planned = 60, actual = 60), poured(1, planned = 30, actual = 30), neverStarted(2, 45))

        assertThat(RecipeOffer.of(input(session))).containsExactly(
            RecipeStep(StepKind.INFUSION, 60),
            RecipeStep(StepKind.INFUSION, 30),
            RecipeStep(StepKind.INFUSION, 45),
        ).inOrder()
    }

    @Test
    fun `a skipped and a never started step are offered as planned`() {
        val skipped = SessionStepRow(
            position = 1, kind = StepKind.INFUSION, plannedSeconds = 30, tempC = 60, actualSeconds = 0,
            pausedSeconds = 0, overtimeSeconds = 0, startedAtWallMs = null, endedAtWallMs = null, skipped = true,
            overtimeAutoAck = false,
        )
        val session = session(poured(0, planned = 60, actual = 40), skipped, neverStarted(2, 90))
        val recipe = RECIPE.copy(steps = RECIPE.steps + RecipeStep(StepKind.INFUSION, 90))

        assertThat(RecipeOffer.of(input(session, recipe = recipe))).containsExactly(
            RecipeStep(StepKind.INFUSION, 40),
            RecipeStep(StepKind.INFUSION, 30),
            RecipeStep(StepKind.INFUSION, 90),
        ).inOrder()
    }

    @Test
    fun `a skipped or never started step alone is no deliberate change`() {
        val skipped = poured(1, planned = 30, actual = 0).copy(skipped = true)

        assertThat(RecipeOffer.of(input(session(poured(0, planned = 60, actual = 60), skipped)))).isNull()
        assertThat(RecipeOffer.of(input(session(poured(0, planned = 60, actual = 60), neverStarted(1, 30))))).isNull()
    }

    @Test
    fun `recorded times round to 5 s below 10 min and to whole minutes from 10 min, at least 5 s`() {
        assertThat(RecipeOffer.recordedTime(47)).isEqualTo(45)
        assertThat(RecipeOffer.recordedTime(48)).isEqualTo(50)
        assertThat(RecipeOffer.recordedTime(2)).isEqualTo(5)
        assertThat(RecipeOffer.recordedTime(1)).isEqualTo(5)
        assertThat(RecipeOffer.recordedTime(597)).isEqualTo(595)
        assertThat(RecipeOffer.recordedTime(598)).isEqualTo(600)
        assertThat(RecipeOffer.recordedTime(600)).isEqualTo(600)
        assertThat(RecipeOffer.recordedTime(629)).isEqualTo(600)
        assertThat(RecipeOffer.recordedTime(630)).isEqualTo(660)
    }

    @Test
    fun `a long step ended early is offered in whole minutes`() {
        val session = session(poured(0, planned = 900, actual = 755))
        val recipe = RECIPE.copy(steps = listOf(RecipeStep(StepKind.INFUSION, 900)))

        assertThat(RecipeOffer.of(input(session, recipe = recipe))).containsExactly(RecipeStep(StepKind.INFUSION, 780))
    }

    @Test
    fun `a recipe changed after the session ended offers nothing, one changed before still does`() {
        val session = session(poured(0, planned = 60, actual = 48), poured(1, planned = 30, actual = 30))

        assertThat(RecipeOffer.of(input(session, changedAt = ENDED + 1))).isNull()
        assertThat(RecipeOffer.of(input(session, changedAt = ENDED))).isNotNull()
    }

    @Test
    fun `a recipe that is gone or a tea not in the collection offers nothing`() {
        val session = session(poured(0, planned = 60, actual = 48), poured(1, planned = 30, actual = 30))

        assertThat(RecipeOffer.of(input(session, recipe = null))).isNull()
        assertThat(RecipeOffer.of(input(session, teaInCollection = false))).isNull()
        assertThat(RecipeOffer.of(input(session, recipe = RECIPE.copy(id = "other")))).isNull()
    }

    @Test
    fun `an offer already accepted is not offered again`() {
        val session = session(poured(0, planned = 60, actual = 48), poured(1, planned = 30, actual = 30))

        assertThat(RecipeOffer.of(input(session, applied = true))).isNull()
    }

    @Test
    fun `a session in progress offers nothing`() {
        val session = session(poured(0, planned = 60, actual = 48)).copy(status = SessionStatus.IN_PROGRESS)

        assertThat(RecipeOffer.of(input(session))).isNull()
    }

    @Test
    fun `temperatures compare with the recipe's base and step notes are left out`() {
        // The recipe states the base explicitly and carries a note: the same plan, so nothing to offer.
        val recipe = RECIPE.copy(
            steps = listOf(
                RecipeStep(StepKind.INFUSION, 60, tempC = 60, note = "sanft"),
                RecipeStep(StepKind.INFUSION, 30),
            ),
        )
        val session = session(poured(0, planned = 60, actual = 60), poured(1, planned = 30, actual = 30))

        assertThat(RecipeOffer.of(input(session, recipe = recipe))).isNull()
    }

    @Test
    fun `a legacy session without rows for its never started steps keeps the recipe's steps there`() {
        // Completed by v0.3.0 or earlier: „2 von 3 Aufgüssen", only the two ended rows are stored (TMR-12).
        val recipe = RECIPE.copy(steps = RECIPE.steps + RecipeStep(StepKind.INFUSION, 90, tempC = 70))
        val asPlanned = session(poured(0, planned = 60, actual = 60), poured(1, planned = 30, actual = 30))
            .copy(plannedStepCount = 3)
        val endedEarly = session(poured(0, planned = 60, actual = 48), poured(1, planned = 30, actual = 30))
            .copy(plannedStepCount = 3)

        assertThat(RecipeOffer.of(input(asPlanned, recipe = recipe))).isNull()
        assertThat(RecipeOffer.of(input(endedEarly, recipe = recipe))).containsExactly(
            RecipeStep(StepKind.INFUSION, 50),
            RecipeStep(StepKind.INFUSION, 30),
            RecipeStep(StepKind.INFUSION, 90, tempC = 70),
        ).inOrder()
    }

    @Test
    fun `a legacy session missing rows the recipe cannot fill offers nothing`() {
        val session = session(poured(0, planned = 60, actual = 48), poured(1, planned = 30, actual = 30))
            .copy(plannedStepCount = 3)

        assertThat(RecipeOffer.of(input(session))).isNull()
    }

    @Test
    fun `a note is kept at an unchanged position and when only the step's time changed`() {
        val old = listOf(
            RecipeStep(StepKind.INFUSION, 60, note = "sanft"),
            RecipeStep(StepKind.INFUSION, 30, note = "heiß"),
        )
        val proposal = listOf(RecipeStep(StepKind.INFUSION, 50), RecipeStep(StepKind.INFUSION, 30))

        assertThat(RecipeOffer.keepingNotes(proposal, old)).containsExactly(
            RecipeStep(StepKind.INFUSION, 50, note = "sanft"),
            RecipeStep(StepKind.INFUSION, 30, note = "heiß"),
        ).inOrder()
    }

    @Test
    fun `a position whose kind changed gets no note, an added step gets none`() {
        val old = listOf(RecipeStep(StepKind.RINSE, 10, note = "kurz"), RecipeStep(StepKind.INFUSION, 60, note = "x"))
        val proposal = listOf(
            RecipeStep(StepKind.INFUSION, 10),
            RecipeStep(StepKind.INFUSION, 60),
            RecipeStep(StepKind.INFUSION, 45),
        )

        assertThat(RecipeOffer.keepingNotes(proposal, old)).containsExactly(
            RecipeStep(StepKind.INFUSION, 10),
            RecipeStep(StepKind.INFUSION, 60, note = "x"),
            RecipeStep(StepKind.INFUSION, 45),
        ).inOrder()
    }

    @Test
    fun `a recipe without notes behaves as before`() {
        val proposal = listOf(RecipeStep(StepKind.INFUSION, 50), RecipeStep(StepKind.INFUSION, 30))

        assertThat(RecipeOffer.keepingNotes(proposal, RECIPE.steps)).isEqualTo(proposal)
    }

    @Test
    fun `a note alone never produces an offer`() {
        val recipe = RECIPE.copy(steps = RECIPE.steps.map { it.copy(note = "n") })
        val session = session(poured(0, planned = 60, actual = 60), poured(1, planned = 30, actual = 30))

        assertThat(RecipeOffer.of(input(session, recipe = recipe))).isNull()
    }

    private fun input(
        session: BrewSession,
        recipe: Recipe? = RECIPE,
        changedAt: Long = ENDED - 10_000,
        applied: Boolean = false,
        teaInCollection: Boolean = true,
    ) = RecipeOfferInput(session, applied, recipe, changedAt, teaInCollection)

    private fun session(vararg steps: SessionStepRow) = BrewSession(
        id = "s1",
        status = SessionStatus.COMPLETED,
        snapshot = SessionSnapshot(
            teaId = "tea", teaName = "Morgentau", teaSecondaryName = null, teaType = null, styleKey = null,
            category = null, profileId = RECIPE.id, profileName = "Kyusu", method = BrewMethod.KYUSU,
            vesselId = null, vesselName = null, leafGrams = 5.0, waterMl = 250, baseTempC = 60,
        ),
        preAlertSeconds = 10,
        plannedStepCount = steps.size,
        costCents = null,
        startedAtMs = ENDED - 600_000,
        endedAtMs = ENDED,
        zoneId = "Europe/Berlin",
        autoFinished = false,
        steps = steps.toList(),
    )

    private fun poured(position: Int, planned: Int, actual: Int, overtime: Int = 0) = SessionStepRow(
        position = position, kind = StepKind.INFUSION, plannedSeconds = planned, tempC = 60, actualSeconds = actual,
        pausedSeconds = 0, overtimeSeconds = overtime, startedAtWallMs = 1L, endedAtWallMs = 2L, skipped = false,
        overtimeAutoAck = false,
    )

    private fun neverStarted(position: Int, planned: Int) = SessionStepRow(
        position = position, kind = StepKind.INFUSION, plannedSeconds = planned, tempC = 60, actualSeconds = null,
        pausedSeconds = null, overtimeSeconds = null, startedAtWallMs = null, endedAtWallMs = null, skipped = false,
        overtimeAutoAck = false,
    )

    private companion object {
        const val ENDED = 1_800_000_000_000L

        val RECIPE = Recipe(
            id = "r1",
            teaId = "tea",
            name = "Kyusu",
            method = BrewMethod.KYUSU,
            leafGrams = 5.0,
            waterMl = 250,
            baseTempC = 60,
            steps = listOf(RecipeStep(StepKind.INFUSION, 60), RecipeStep(StepKind.INFUSION, 30)),
        )
    }
}
