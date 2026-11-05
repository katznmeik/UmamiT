package com.umamit.core.model

/**
 * A recipe (BrewProfile, 05 §3.2) with its ordered steps (05 §3.3).
 * Temperatures are °C in multiples of 5 (GEN-12).
 */
data class Recipe(
    val id: String,
    val teaId: String,
    val name: String,
    val method: BrewMethod,
    val leafGrams: Double,
    val waterMl: Int,
    val baseTempC: Int,
    val steps: List<RecipeStep>,
    val vesselId: String? = null,
    /**
     * The base B of the steps' automatic pre-alert (TMR-23, D-093): null = global setting; 0 = off; otherwise
     * 5–30 s. Each step has its own choice ([RecipeStep.preAlertSeconds]).
     */
    val preAlertSeconds: Int? = null,
    val isDefault: Boolean = false,
    val position: Int = 0,
    val source: RecipeSource = RecipeSource.USER,
    val notes: String? = null,
) {
    companion object {
        /** A tea holds 1 to this many recipes (PRF-01, D-100). */
        const val MAX_PER_TEA = 10
    }
}

/** One step of a recipe (05 §3.3); its order is its index in [Recipe.steps]. */
data class RecipeStep(
    val kind: StepKind,
    val durationSeconds: Int,
    /** null = the recipe's base temperature. */
    val tempC: Int? = null,
    val note: String? = null,
    /** The step's pre-alert (TMR-23, D-093): null *Automatisch*, 0 *Aus*, otherwise 5–30 s. */
    val preAlertSeconds: Int? = null,
)
