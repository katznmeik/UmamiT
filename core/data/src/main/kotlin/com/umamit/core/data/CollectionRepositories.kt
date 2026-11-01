package com.umamit.core.data

import com.umamit.core.model.InventoryEvent
import com.umamit.core.model.Photo
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StockSummary
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaOverview
import com.umamit.core.model.UserSettings
import com.umamit.core.model.Vessel
import kotlinx.coroutines.flow.Flow

/** The teas of the collection (05 §3.1) with their default recipe and cover photo. */
interface TeaRepository {
    /** All teas, archived ones included, sorted by name (case-insensitive). */
    fun observeTeas(): Flow<List<TeaOverview>>

    /** The teas that are not archived, sorted by name: Start and the tea picker (SCR-02, SCR-03, TEA-04). */
    fun observeOwnedTeas(): Flow<List<TeaOverview>>

    fun observeTea(id: String): Flow<TeaOverview?>

    suspend fun getTea(id: String): Tea?

    /** Inserts the tea, or updates it when its id exists (keeping its creation time). */
    suspend fun saveTea(tea: Tea)

    /**
     * Deletes the tea row; its recipes, stock events and photo rows go with it (05 §3). The user's *Löschen*
     * is [TeaArchive.delete], which also guards the active brew and removes the photo files (D-100).
     */
    suspend fun deleteTea(id: String)

    /** Stores a photo row (05 §3.9); the image file itself is written by the caller. */
    suspend fun savePhoto(photo: Photo)

    /** Sets or clears the tea's cover photo. */
    suspend fun setCoverPhoto(teaId: String, photoId: String?)

    /**
     * *Rezept passt* (CAT-05, SCR-06): clears the tea's „Rezept prüfen" reason and nothing else;
     * no other tea changes. A tea that is gone is ignored.
     */
    suspend fun clearRecipeCheck(teaId: String)
}

/** Recipes with their ordered steps (05 §3.2, §3.3). Temperatures are snapped to 5 °C (GEN-12). */
interface RecipeRepository {
    /** The tea's recipes in their position order, each with its steps in order. */
    fun observeRecipes(teaId: String): Flow<List<Recipe>>

    suspend fun getRecipe(id: String): Recipe?

    /**
     * Inserts or updates the recipe and replaces its steps, in one transaction (PRF-01, D-100):
     * - a recipe saved as default becomes the tea's only default; a tea without a default makes its
     *   first recipe (by position) the default, so it always has exactly one;
     * - a new recipe of a tea that already has [Recipe.MAX_PER_TEA] is refused ([SaveRecipeResult.LimitReached]);
     * - the tea's „Rezept prüfen" is cleared (TEA-17), from the recipe editor and the setup's *Im Rezept speichern*.
     */
    suspend fun saveRecipe(recipe: Recipe): SaveRecipeResult

    /**
     * *Im Rezept speichern* (BRW-02, SCR-31): replaces exactly the steps, each with its pre-alert (TMR-23,
     * D-093); the recipe's pre-alert base B stays. Clears the tea's „Rezept prüfen" (TEA-17).
     */
    suspend fun replaceSteps(recipeId: String, steps: List<RecipeStep>)

    /**
     * Deletes the recipe unless it is its tea's last one (PRF-01, D-100); a deleted default passes to the
     * first remaining recipe by position. One transaction.
     */
    suspend fun deleteRecipe(id: String): DeleteRecipeResult
}

/** How [RecipeRepository.saveRecipe] ended. */
sealed interface SaveRecipeResult {
    data object Saved : SaveRecipeResult

    /** The tea already has [Recipe.MAX_PER_TEA] recipes; nothing was written. */
    data object LimitReached : SaveRecipeResult
}

/** How [RecipeRepository.deleteRecipe] ended. */
sealed interface DeleteRecipeResult {
    data object Deleted : DeleteRecipeResult

    /** The tea's last recipe stays (PRF-01); nothing changed. */
    data object LastRecipe : DeleteRecipeResult

    /** No recipe with the id (any more); nothing changed. */
    data object NotFound : DeleteRecipeResult
}

/** The user's vessels (05 §3.4). */
interface VesselRepository {
    /** All vessels sorted by name. */
    fun observeVessels(): Flow<List<Vessel>>

    suspend fun getVessel(id: String): Vessel?

    suspend fun saveVessel(vessel: Vessel)

    /** Deletes the vessel row. The app never deletes a vessel: it archives it ([VesselArchive], D-100). */
    suspend fun deleteVessel(id: String)
}

/** Stock events and the stock derived from them (05 §3.10). */
interface StockRepository {
    /** The tea's events, oldest first. */
    fun observeEvents(teaId: String): Flow<List<InventoryEvent>>

    fun observeStock(teaId: String): Flow<StockSummary>

    suspend fun addEvent(event: InventoryEvent)

    suspend fun deleteEvent(id: String)

    /**
     * *Bestand korrigieren* (INV-03, BRW-02, D-093 point 7): sets the tea's stock to [newTotalGrams] (0 = leer)
     * with one `ADJUSTMENT` event of the difference to the current stock, no price, at now (05 §3.10).
     * Returns that event, or null when the stock already is [newTotalGrams] (nothing is written).
     *
     * @throws IllegalArgumentException when [newTotalGrams] is negative or not a number.
     */
    suspend fun correctStock(teaId: String, newTotalGrams: Double): InventoryEvent?
}

/** The settings of 05 §6 (M1 subset) and the seed markers. */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    /** Changes settings atomically; a pre-alert outside 0, 5…30 is snapped and clamped (SET-10). */
    suspend fun update(transform: (UserSettings) -> UserSettings)

    /** Whether the first-start seed data has been created (M1-07); never reset. */
    val seedDataCreated: Flow<Boolean>

    suspend fun markSeedDataCreated()

    /** Whether the one-time seed photo download (ONB-06) has run, whatever its outcome; never reset. */
    val seedPhotosAttempted: Flow<Boolean>

    suspend fun markSeedPhotosAttempted()

    /** Whether the seven seed vessels of v0.2.1 have been added (WARE-06, D-093); never reset. */
    val seedVesselsV2Added: Flow<Boolean>

    suspend fun markSeedVesselsV2Added()
}
