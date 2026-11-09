package com.umamit.core.model

import java.time.LocalDate

/** A tea in the user's collection (05 §3.1). Dates are local calendar dates. */
data class Tea(
    val id: String,
    val name: String,
    val category: TeaCategory,
    val form: TeaForm,
    val secondaryName: String? = null,
    val catalogKey: String? = null,
    val styleKey: String? = null,
    val teaType: String? = null,
    val umamiLevel: UmamiLevel? = null,
    val isFlavored: Boolean = false,
    val isOrganic: Boolean = false,
    val ingredients: String? = null,
    val originCountry: String? = null,
    val originRegion: String? = null,
    val cultivar: String? = null,
    val harvestYear: Int? = null,
    val harvestSeason: HarvestSeason? = null,
    val shadingDays: Int? = null,
    val processingNotes: String? = null,
    val description: String? = null,
    val vendor: String? = null,
    val productUrl: String? = null,
    val openedDate: LocalDate? = null,
    val bestBefore: LocalDate? = null,
    val freshnessDays: Int? = null,
    /** The tea's own „Knapp ab" in whole grams 1–500 (SCR-09, D-108); null: SET-16's global threshold. */
    val lowStockGrams: Double? = null,
    /**
     * The stock warning (D-108): off leaves the tea out of Start's card (INV-10) and the reminder (INV-11) and keeps
     * its reorder link a text link (INV-09 AC1); its *Knapp* / *Leer* labels stay. A purchase never switches it on.
     */
    val lowStockWarning: Boolean = true,
    val storageNote: String? = null,
    val notes: String? = null,
    val recipeCheckReason: RecipeCheckReason? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val coverPhotoId: String? = null,
) {
    companion object {
        /** A tea's own „Knapp ab" (SCR-09, D-108): whole grams from 1 to 500. */
        val OWN_LOW_STOCK_GRAMS: IntRange = 1..500
    }
}

/** A tea with what lists and the brew setup show next to it: its default recipe and cover photo. */
data class TeaOverview(val tea: Tea, val defaultRecipe: Recipe?, val coverPhoto: Photo?)
