package com.umamit.core.data

import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.domain.brew.RecipeOffer
import com.umamit.core.domain.brew.RecipeOfferInput
import com.umamit.core.model.RecipeStep
import javax.inject.Inject

/**
 * [RecipeOffers] on Room (PRF-07, 05 §3.7 „Accept the recipe offer", D-112). „The recipe has not changed since the
 * session" is read from the recipe's and its steps' `created_at` / `updated_at` against the session's `ended_at`.
 * It reads the session, its steps, the recipe and the tea, and writes only the recipe's steps and the session's
 * `times_applied`; `active_brew` and the timer are never touched.
 */
class RoomRecipeOffers @Inject constructor(private val db: UmamiDatabase, private val time: WallClock) :
    RecipeOffers {
    private val sessions = db.sessionDao()
    private val recipes = db.recipeDao()

    override suspend fun proposal(sessionId: String): List<RecipeStep>? = db.withTransaction {
        sessions.get(sessionId)?.let { offerFor(it)?.second }
    }

    override suspend fun accept(sessionId: String): RecipeOfferAccept {
        val now = time.nowMs()
        return db.withTransaction {
            val stored = sessions.get(sessionId)
            val offer = stored?.let { offerFor(it) }
            if (stored == null || offer == null) {
                RecipeOfferAccept.NOT_APPLICABLE
            } else {
                val (recipeId, steps) = offer
                // *Im Rezept speichern*'s write (BRW-02, SCR-31): only the steps, each with its pre-alert; the
                // recipe's notes stay by position and kind (D-112 Q22).
                val oldSteps = recipes.get(recipeId)?.toModel()?.steps.orEmpty()
                val kept = RecipeOffer.keepingNotes(steps, oldSteps)
                recipes.replaceSteps(recipeId, kept.toEntities(recipeId, now), now)
                sessions.update(stored.copy(timesApplied = true, updatedAt = now))
                RecipeOfferAccept.ACCEPTED
            }
        }
    }

    /** The session's recipe id and the proposal for it when the offer applies; null otherwise. */
    private suspend fun offerFor(stored: BrewSessionEntity): Pair<String, List<RecipeStep>>? {
        val recipeId = stored.profileId ?: return null
        val recipe = recipes.get(recipeId)
        val tea = recipe?.let { db.teaDao().get(it.profile.teaId) }
        val changedAt = recipe?.let { row ->
            val stepTimes = row.steps.flatMap { listOf(it.createdAt, it.updatedAt) }
            (stepTimes + row.profile.createdAt + row.profile.updatedAt).max()
        }
        val input = RecipeOfferInput(
            session = stored.toModel(sessions.steps(stored.id)),
            applied = stored.timesApplied,
            recipe = recipe?.toModel(),
            recipeChangedAtMs = changedAt,
            teaInCollection = tea != null && !tea.isArchived,
        )
        return RecipeOffer.of(input)?.let { recipeId to it }
    }
}
