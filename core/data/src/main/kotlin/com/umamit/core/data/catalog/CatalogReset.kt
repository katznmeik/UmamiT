package com.umamit.core.data.catalog

import android.database.SQLException
import androidx.room.withTransaction
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.toModel
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeTemplate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * The reset a tea's ⋮ offers: [leftOut] of the catalog's recipes don't fit beside the tea's own recipes under
 * [Recipe.MAX_PER_TEA] and are left out (0: all fit).
 */
data class CatalogResetPlan(val leftOut: Int)

/** How [CatalogReset.reset] ended. */
sealed interface CatalogResetResult {
    /** The tea took the catalog's description and recipes; [leftOut] catalog recipes didn't fit. */
    data class Reset(val leftOut: Int) : CatalogResetResult

    /** The tea links to no catalog tea, or the catalog holds it no more; nothing was written. */
    data object NotInCatalog : CatalogResetResult

    /** The database failed, or an unexpected error (D-090); nothing was written. */
    data object Failed : CatalogResetResult
}

/**
 * *Aus Katalog zurücksetzen* (PRF-06, CAT-13, D-104): a collection tea takes its catalog tea's current
 * description and recipes, derived as *In Sammlung aufnehmen* derives them ([CatalogDerivation]).
 *
 * - **Recipes:** the catalog recipes (every source but [RecipeSource.USER]; an edit makes a recipe the user's)
 *   are replaced by the derived ones, placed first, the default first; the user's recipes follow in their order.
 * - **Default:** a catalog default passes to the new catalog default; the user's own default stays.
 * - **Limit:** beyond [Recipe.MAX_PER_TEA] the catalog recipes over the limit are left out.
 * - **„Rezept prüfen"** is cleared (TEA-17); stock, photos, notes, favourite, archive flag and sessions stay.
 * - **Rule 8:** a running brew keeps its snapshot; its recipe row is rewritten, never deleted, so the session
 *   keeps its link.
 */
interface CatalogReset {
    /** The reset of tea [teaId], or null when it isn't offered (no catalog key, or no catalog tea for it). */
    fun observePlan(teaId: String): Flow<CatalogResetPlan?>

    /** Resets tea [teaId] in one transaction. */
    suspend fun reset(teaId: String): CatalogResetResult
}

/** [CatalogReset] from `catalog.db` into `umamit.db`. */
class RoomCatalogReset @Inject constructor(
    private val db: UmamiDatabase,
    private val teas: TeaRepository,
    private val recipes: RecipeRepository,
    private val catalog: CatalogDao,
) : CatalogReset {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePlan(teaId: String): Flow<CatalogResetPlan?> = teas.observeTea(teaId)
        .map { it?.tea?.catalogKey }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key == null) {
                flowOf(null)
            } else {
                combine(catalog.observeTea(key), recipes.observeRecipes(teaId)) { entry, list ->
                    entry?.let { CatalogResetPlan(split(CatalogDerivation.templates(it).size, list).leftOut) }
                }
            }
        }
        .distinctUntilChanged()

    override suspend fun reset(teaId: String): CatalogResetResult = try {
        val key = teas.getTea(teaId)?.catalogKey
        val entry = key?.let { catalog.tea(it) }
        if (entry == null) {
            CatalogResetResult.NotInCatalog
        } else {
            withContext(NonCancellable + Dispatchers.IO) {
                val templates = CatalogDerivation.templates(entry)
                db.withTransaction { write(teaId, templates, CatalogDerivation.description(entry)) }
            }
        }
    } catch (_: SQLException) {
        CatalogResetResult.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Rule 8: the timer runs in this process; a bug in the reset fails the reset, never the app (D-090).
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: RuntimeException,
    ) {
        CatalogResetResult.Failed
    }

    private suspend fun write(
        teaId: String,
        templates: List<RecipeTemplate>,
        description: String?,
    ): CatalogResetResult {
        // Read again inside the transaction: the tea may have changed (or gone) since.
        val tea = teas.getTea(teaId) ?: return CatalogResetResult.NotInCatalog
        val dao = db.recipeDao()
        val current = dao.forTea(teaId).map { it.toModel() }
        val split = split(templates.size, current)
        val own = current.filter { it.source == RecipeSource.USER }
        val oldCatalog = current.filter { it.source != RecipeSource.USER }
        val catalogDefault = own.none { it.isDefault }
        // The active brew's recipe keeps its row (rule 8): its id goes to the first new catalog recipe.
        val active = dao.activeRecipeId()
        val reused = oldCatalog.map { it.id }.sortedByDescending { it == active }
        val kept = templates.take(split.fits)
        reused.drop(kept.size).forEach { recipes.deleteRecipe(it) }
        kept.forEachIndexed { position, template ->
            val id = reused.getOrNull(position) ?: UUID.randomUUID().toString()
            val recipe = template.toRecipe(id, teaId, position).copy(isDefault = catalogDefault && position == 0)
            recipes.saveRecipe(recipe)
        }
        own.forEachIndexed { index, recipe -> recipes.saveRecipe(recipe.copy(position = kept.size + index)) }
        // Saving a recipe clears „Rezept prüfen" (TEA-17); the tea is written last with the new description.
        teas.saveTea(tea.copy(description = description, recipeCheckReason = null))
        return CatalogResetResult.Reset(split.leftOut)
    }

    /** How [derived] catalog recipes fit beside [current]'s own recipes under [Recipe.MAX_PER_TEA]. */
    private fun split(derived: Int, current: List<Recipe>): Split {
        val room = Recipe.MAX_PER_TEA - current.count { it.source == RecipeSource.USER }
        val fits = minOf(derived, room.coerceAtLeast(0))
        return Split(fits, derived - fits)
    }

    private data class Split(val fits: Int, val leftOut: Int)
}

/** Binds [CatalogReset] (PRF-06). */
@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogResetModule {
    @Binds
    abstract fun catalogReset(impl: RoomCatalogReset): CatalogReset
}
