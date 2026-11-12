package com.umamit.core.model

// The tasting inputs of a session (JRN-02–JRN-05, 05 §3.6–§3.8, D-112, D-114). Every one is optional: a session
// without any of them is complete, and nothing marks it. From v0.6.1 (umamit.db v7, D-114) the taste and a short
// note belong to each infusion; rating, notes and descriptors stay per session.

/** The six taste axes of JRN-03, in the order the sliders and the radar show them; each a `taste_*` column. */
enum class TasteAxis {
    UMAMI,
    SWEETNESS,
    BITTERNESS,
    ASTRINGENCY,
    BODY,
    AROMA,
}

/** An infusion's taste values, 0–5 per [TasteAxis]; an axis left untouched is null, not 0 (JRN-03). */
data class TasteProfile(
    val umami: Int? = null,
    val sweetness: Int? = null,
    val bitterness: Int? = null,
    val astringency: Int? = null,
    val body: Int? = null,
    val aroma: Int? = null,
) {
    operator fun get(axis: TasteAxis): Int? = when (axis) {
        TasteAxis.UMAMI -> umami
        TasteAxis.SWEETNESS -> sweetness
        TasteAxis.BITTERNESS -> bitterness
        TasteAxis.ASTRINGENCY -> astringency
        TasteAxis.BODY -> body
        TasteAxis.AROMA -> aroma
    }

    /** How many axes are set. */
    val setCount: Int get() = TasteAxis.entries.count { get(it) != null }

    /** This profile with [axis] set to [value] (null unsets it). */
    fun with(axis: TasteAxis, value: Int?): TasteProfile = when (axis) {
        TasteAxis.UMAMI -> copy(umami = value)
        TasteAxis.SWEETNESS -> copy(sweetness = value)
        TasteAxis.BITTERNESS -> copy(bitterness = value)
        TasteAxis.ASTRINGENCY -> copy(astringency = value)
        TasteAxis.BODY -> copy(body = value)
        TasteAxis.AROMA -> copy(aroma = value)
    }

    companion object {
        val EMPTY = TasteProfile()

        /**
         * The average per axis over [profiles], each axis over the profiles that set it (JRN-03); an axis none set
         * is absent.
         */
        fun averages(profiles: Collection<TasteProfile>): Map<TasteAxis, Double> = buildMap {
            TasteAxis.entries.forEach { axis ->
                val values = profiles.mapNotNull { it[axis] }
                if (values.isNotEmpty()) put(axis, values.average())
            }
        }
    }
}

/**
 * What a `session_step` row holds of the tasting (05 §3.7, umamit.db v7, D-114): its taste and its short note
 * (trimmed, never blank, ≤ 200). Only ended rows that are not *Spülen* are written, but a v0.6.0 session whose
 * steps never brewed keeps its values on the row the migration chose (05 §9).
 */
data class StepTasting(val taste: TasteProfile = TasteProfile.EMPTY, val note: String? = null) {
    val isEmpty: Boolean get() = taste == TasteProfile.EMPTY && note == null

    companion object {
        val EMPTY = StepTasting()
    }
}

/**
 * One infusion of a session as the tasting sees it (JRN-03, JRN-05, D-114): an ended step that is not *Spülen*.
 *
 * @property position the step's `position`, which addresses the tasting writes.
 * @property number the step table's number („2. Aufguss", counting *Aufguss* rows by position); null for a step of
 * another kind (*Aufschlagen*), which the table does not number.
 */
data class Infusion(val position: Int, val number: Int?, val taste: TasteProfile, val note: String?)

/** The bounds of the tasting inputs (05 §3.6, §3.8). */
object TastingLimits {
    const val MIN_RATING = 0.5
    const val MAX_RATING = 5.0

    /** Ratings go in half stars. */
    const val RATING_STEP = 0.5
    const val MIN_TASTE = 0
    const val MAX_TASTE = 5

    /** The notes' longest length after trimming, in characters (UTF-16 units, as a text field counts). */
    const val MAX_NOTES_LENGTH = 5000

    /** An infusion note's longest length after trimming (JRN-05, D-114 Q17). */
    const val MAX_INFUSION_NOTE_LENGTH = 200

    /** A session holds at most this many descriptors (JRN-04). */
    const val MAX_DESCRIPTORS = 20

    /** Whether [rating] is one of 0,5 … 5,0 in half steps. */
    fun isValidRating(rating: Double): Boolean =
        rating in MIN_RATING..MAX_RATING && (rating / RATING_STEP).let { it == Math.rint(it) }

    fun isValidTaste(value: Int): Boolean = value in MIN_TASTE..MAX_TASTE
}

/** A tea's average rating over its rated sessions and how many there are (TEA-08, TEA-13). */
data class TeaRating(val average: Double, val count: Int)

/** How often a descriptor key was ticked on a tea's sessions (TEA-13 GESCHMACK). */
data class DescriptorCount(val key: String, val count: Int)

/**
 * A tea's tasting summary (TEA-13, JRN-03): [rating] null without a rated session; [tasteAverages] per axis over
 * every infusion of its finished sessions that set that axis (D-114; an axis none set is absent); [descriptorCounts]
 * every stored key, most frequent first (then by key), unknown keys included: the UI shows only keys it knows.
 */
data class TeaTasting(
    val rating: TeaRating?,
    val tasteAverages: Map<TasteAxis, Double>,
    val descriptorCounts: List<DescriptorCount>,
) {
    companion object {
        val EMPTY = TeaTasting(rating = null, tasteAverages = emptyMap(), descriptorCounts = emptyList())
    }
}
