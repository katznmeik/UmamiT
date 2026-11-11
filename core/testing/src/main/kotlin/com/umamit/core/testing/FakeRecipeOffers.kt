package com.umamit.core.testing

import com.umamit.core.data.RecipeOfferAccept
import com.umamit.core.data.RecipeOffers
import com.umamit.core.model.RecipeStep

/**
 * [RecipeOffers] in memory (PRF-07): a test puts the proposal of a session into [proposals] when the offer applies.
 * Accepting removes it (the offer is gone, `times_applied`) and records the session in [accepted]; a session without a
 * proposal is refused.
 */
class FakeRecipeOffers : RecipeOffers {
    val proposals = mutableMapOf<String, List<RecipeStep>>()

    /** The sessions whose offer was accepted, in order. */
    val accepted = mutableListOf<String>()

    /** When set, every read and accept throws it (a failing database). */
    var failure: Exception? = null

    override suspend fun proposal(sessionId: String): List<RecipeStep>? {
        failure?.let { throw it }
        return proposals[sessionId]
    }

    override suspend fun accept(sessionId: String): RecipeOfferAccept {
        failure?.let { throw it }
        if (proposals.remove(sessionId) == null) return RecipeOfferAccept.NOT_APPLICABLE
        accepted += sessionId
        return RecipeOfferAccept.ACCEPTED
    }
}
