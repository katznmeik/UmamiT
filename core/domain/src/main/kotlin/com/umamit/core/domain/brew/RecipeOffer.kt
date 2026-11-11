package com.umamit.core.domain.brew

import com.umamit.core.model.BrewSession
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.SessionStepRow

/**
 * What *Zeiten ins Rezept übernehmen* (PRF-07, D-112) reads of one finished session, as stored now.
 *
 * @property session the session with its step rows.
 * @property applied the session's `times_applied`: the offer was accepted for it.
 * @property recipe the session's recipe as stored now (`profile_id`); null when it is gone.
 * @property recipeChangedAtMs the latest `created_at` / `updated_at` of the recipe and its steps.
 * @property teaInCollection the recipe's tea is in the collection (not deleted, not archived).
 */
data class RecipeOfferInput(
    val session: BrewSession,
    val applied: Boolean,
    val recipe: Recipe?,
    val recipeChangedAtMs: Long?,
    val teaInCollection: Boolean,
)

/**
 * The recipe offer's rule (PRF-07, D-112), derived whenever the „Gespeichert" sheet or the session page shows; nothing
 * of it is stored (`times_offered` stays unwritten).
 *
 * Only deliberate changes count: an infusion ended early (*Aufguss jetzt beenden*) and the steps edited or added in
 * the Schritte-Sheet without *Im Rezept speichern*. Pouring late after the alarm never counts.
 */
object RecipeOffer {
    /** From this recorded time on, an early-ended step's time is rounded to whole minutes (the stepper's grid). */
    const val MINUTE_ROUNDING_FROM_SECONDS = 600

    private const val FINE_STEP_SECONDS = 5
    private const val MINUTE_SECONDS = 60

    /**
     * The proposal for [input]'s recipe when the offer applies, else null. It applies when the session is completed
     * and has a recipe that still exists and whose tea is in the collection, the offer was not accepted for it, the
     * recipe has not changed since the session ended, and the proposal differs from the recipe's steps.
     */
    fun of(input: RecipeOfferInput): List<RecipeStep>? {
        val session = input.session
        val recipe = input.recipe
        val endedAt = session.endedAtMs
        val changedAt = input.recipeChangedAtMs
        val applies = recipe != null && endedAt != null && changedAt != null &&
            session.status == SessionStatus.COMPLETED &&
            session.snapshot.profileId == recipe.id &&
            input.teaInCollection &&
            !input.applied &&
            changedAt <= endedAt
        return if (applies) {
            proposal(session, recipe.baseTempC).takeIf { it != recipe.steps.comparable(recipe.baseTempC) }
        } else {
            null
        }
    }

    /**
     * The session's step plan as it ended, as recipe steps of a recipe with [recipeBaseTempC]: kinds, temperatures
     * (null where it is the recipe's base, as *Im Rezept speichern* stores them) and pre-alerts, edited and added steps
     * included, each early-ended step with its recorded time ([recordedTime]); every other step, a late-poured, a
     * skipped or a never started one, as planned. Step notes are not part of a session and stay out.
     */
    fun proposal(session: BrewSession, recipeBaseTempC: Int): List<RecipeStep> =
        session.steps.sortedBy { it.position }.map { row ->
            val temp = row.tempC ?: session.snapshot.baseTempC ?: recipeBaseTempC
            RecipeStep(
                kind = row.kind,
                durationSeconds = row.earlyEndedSeconds()?.let(::recordedTime) ?: row.plannedSeconds,
                tempC = temp.takeIf { it != recipeBaseTempC },
                preAlertSeconds = row.preAlertSeconds,
            )
        }

    /** [actualSeconds] rounded half up to 5 s, or to 1 min from [MINUTE_ROUNDING_FROM_SECONDS]; at least 5 s. */
    fun recordedTime(actualSeconds: Int): Int {
        val unit = if (actualSeconds >= MINUTE_ROUNDING_FROM_SECONDS) MINUTE_SECONDS else FINE_STEP_SECONDS
        val rounded = (actualSeconds + unit / 2) / unit * unit
        return maxOf(FINE_STEP_SECONDS, rounded)
    }

    /** The recorded time of a step ended before its set time; null for any other step. */
    private fun SessionStepRow.earlyEndedSeconds(): Int? = actualSeconds?.takeIf { !skipped && it < plannedSeconds }

    /** The recipe's steps in the proposal's terms: temperatures null at the base, notes left out. */
    private fun List<RecipeStep>.comparable(baseTempC: Int): List<RecipeStep> = map { step ->
        step.copy(tempC = step.tempC?.takeIf { it != baseTempC }, note = null)
    }
}
