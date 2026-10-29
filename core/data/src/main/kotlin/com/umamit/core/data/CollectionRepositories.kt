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

    fun observeTea(id: String): Flow<TeaOverview?>

    suspend fun getTea(id: String): Tea?

    /** Inserts the tea, or updates it when its id exists (keeping its creation time). */
    suspend fun saveTea(tea: Tea)

    /** Deletes the tea; its recipes, stock events and photos go with it (05 §3). */
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
     * Inserts or updates the recipe and replaces its steps. A recipe saved as default
     * becomes the tea's only default.
     */
    suspend fun saveRecipe(recipe: Recipe)

    /**
     * *Im Rezept speichern* (BRW-02, SCR-31): replaces exactly the steps, each with its pre-alert (TMR-23,
     * D-093); the recipe's pre-alert base B stays.
     */
    suspend fun replaceSteps(recipeId: String, steps: List<RecipeStep>)

    suspend fun deleteRecipe(id: String)
}

/** The user's vessels (05 §3.4). */
interface VesselRepository {
    /** All vessels sorted by name. */
    fun observeVessels(): Flow<List<Vessel>>

    suspend fun getVessel(id: String): Vessel?

    suspend fun saveVessel(vessel: Vessel)

    suspend fun deleteVessel(id: String)
}

/** Stock events and the stock derived from them (05 §3.10). */
interface StockRepository {
    /** The tea's events, oldest first. */
    fun observeEvents(teaId: String): Flow<List<InventoryEvent>>

    fun observeStock(teaId: String): Flow<StockSummary>

    suspend fun addEvent(event: InventoryEvent)

    suspend fun deleteEvent(id: String)
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
}
