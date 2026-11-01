package com.umamit.feature.collection.detail

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.Recipe
import com.umamit.core.model.StockLevel
import com.umamit.core.model.UmamiLevel
import java.time.LocalDate

/** The tea detail (SCR-09). */
sealed interface TeaDetailUiState {
    data object Loading : TeaDetailUiState

    /** No tea with the route's id (deleted meanwhile). */
    data object Gone : TeaDetailUiState

    data class Ready(val tea: TeaDetail) : TeaDetailUiState
}

/**
 * What SCR-09 shows of a collection tea in v0.4.0 (D-100).
 *
 * @property teaType the *Sorte* for the chip („Sencha"), if any.
 * @property photoPath the cover photo's file, if it exists.
 * @property sessionCount the tea's finished sessions (the `StatTriplet`'s *Sitzungen*).
 * @property lastBrewed the day of the latest finished session in its own time zone; null when never brewed.
 * @property stockGrams the remaining grams (Σ of the stock events; 0 without any, as the setup reads it).
 * @property stockLevel *Knapp* / *Leer* by the tea's own threshold, else SET-16's (INV-05).
 * @property recipes the recipes by position, the default marked.
 * @property reorder INV-09's link, or null when it offers none.
 * @property inActiveBrew the tea of the active brew session: *Archivieren* and *Löschen* are greyed out (E15).
 * @property sessionLeafGrams the leaf the active session of this tea still deducts at its end, for the stock
 *   sheet's line (D-096); null without one or with *Bestand abziehen* off.
 */
data class TeaDetail(
    val id: String,
    val name: String,
    val secondaryName: String? = null,
    val teaType: String? = null,
    val styleKey: String? = null,
    val umamiLevel: UmamiLevel? = null,
    val photoPath: String? = null,
    val sessionCount: Int = 0,
    val lastBrewed: LocalDate? = null,
    val stockGrams: Double = 0.0,
    val stockLevel: StockLevel = StockLevel.EMPTY,
    val recipes: List<RecipeSummary> = emptyList(),
    val reorder: Reorder? = null,
    val description: String? = null,
    val notes: String? = null,
    val details: TeaFacts = TeaFacts(),
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val inActiveBrew: Boolean = false,
    val sessionLeafGrams: Double? = null,
) {
    /** *Rezept hinzufügen* is offered below 10 recipes (PRF-01, D-100). */
    val canAddRecipe: Boolean get() = recipes.size < Recipe.MAX_PER_TEA

    /** *Brühen* is greyed out for an archived tea („Tee ist archiviert", D-100). */
    val canBrew: Boolean get() = !isArchived
}

/**
 * A recipe row of REZEPTE: its key values (vessel, leaf, water, temperature, steps) and whether it is the
 * default.
 *
 * @property infusions the steps that brew (*Aufguss* and *Aufschlagen*; a *Spülen* step brews nothing).
 */
data class RecipeSummary(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val method: BrewMethod,
    val vesselName: String?,
    val leafGrams: Double,
    val waterMl: Int,
    val tempC: Int,
    val infusions: Int,
)

/**
 * *Beim Tee-Kontor nachbestellen* (INV-09): [url] opens in the browser; [prominent] (*Knapp* or *Leer*) shows it
 * as a filled button, else as a text link (AC1).
 */
data class Reorder(val url: String, val prominent: Boolean)

/** DETAILS: the tea's own facts, each shown only when set. */
data class TeaFacts(
    val originCountry: String? = null,
    val originRegion: String? = null,
    val cultivar: String? = null,
    val harvestYear: Int? = null,
    val harvestSeason: HarvestSeason? = null,
    val shadingDays: Int? = null,
    val processing: String? = null,
    val ingredients: String? = null,
    val vendor: String? = null,
    val isOrganic: Boolean = false,
    val isFlavored: Boolean = false,
) {
    val isEmpty: Boolean
        get() = this == TeaFacts() || this == TeaFacts(harvestSeason = HarvestSeason.UNKNOWN)
}
