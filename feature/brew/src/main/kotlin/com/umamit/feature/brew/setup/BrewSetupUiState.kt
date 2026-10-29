package com.umamit.feature.brew.setup

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.KettleWater
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.timer.engine.PlannedStep

/** What the setup (SCR-04) shows. */
sealed interface BrewSetupUiState {
    data object Loading : BrewSetupUiState

    /** The tea (or the session to adjust) no longer exists (GEN-09). */
    data object NotFound : BrewSetupUiState

    /**
     * The setup of one brew (BRW-02). Every value applies to this session only
     * until *Im Rezept speichern*.
     *
     * @property steps the plan; each step carries its temperature (the recipe's base where it had none).
     * @property ratioPer100Ml the leaf per 100 ml of water, shown live („2,0 g / 100 ml", PRF-03 AC1).
     * @property preAlertBaseSeconds the pre-alert base B the session will start with (recipe ?: global, 04 §5):
     *   each step's *Automatisch* lead, listed in the *Vorwarnung* row and shown in the Schritte-Sheet (TMR-23).
     * @property canSaveToRecipe something differs from the selected recipe (BRW-02).
     * @property question „Erst die … -Sitzung beenden?" is open (TMR-21).
     * @property alertKey the alert playing now, or null: nothing may cover it (rule 8).
     */
    data class Ready(
        val teaName: String,
        val secondaryName: String?,
        val stockGrams: Double?,
        val recipeCheckReason: RecipeCheckReason?,
        val recipes: List<RecipeChip>,
        val selectedRecipeId: String?,
        val vessels: List<VesselOption>,
        val selectedVesselId: String?,
        val method: BrewMethod,
        val waterMl: Int,
        val leafGrams: Double,
        val ratioLocked: Boolean,
        val ratioPer100Ml: Double,
        val baseTempC: Int,
        val kettle: Kettle,
        val steps: List<PlannedStep>,
        val preAlertBaseSeconds: Int,
        val canSaveToRecipe: Boolean,
        val question: ActiveSessionQuestion?,
        val alertKey: String?,
    ) : BrewSetupUiState
}

/** A recipe chip (SCR-04). */
data class RecipeChip(val id: String, val name: String)

/** A vessel of the picker with the volume it brews (WARE-02, WARE-04). */
data class VesselOption(val id: String, val name: String, val brewMl: Int)

/** The kettle hint of the first step (BRW-10). */
data class Kettle(val tempC: Int, val water: KettleWater)

/** „Erst die *[teaName]*-Sitzung beenden?" (TMR-21); [teaName] is null for the quick timer. */
data class ActiveSessionQuestion(val teaName: String?)

/** What the setup asks its host to do once. */
enum class BrewSetupEvent {
    /** The session waits in READY: open the timer (TMR-01). */
    OpenTimer,
}
