package com.umamit.feature.collection.receive

import com.umamit.core.backup.recipe.SharedRecipe
import com.umamit.core.backup.recipe.SharedTea
import com.umamit.core.data.AddWithoutCatalog
import com.umamit.core.data.AddWithoutCatalogResult
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.SaveRecipeResult
import com.umamit.core.data.catalog.AddFromCatalog
import com.umamit.core.data.catalog.AddFromCatalogResult
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaForm
import com.umamit.feature.collection.editor.SorteNames
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/** Where SCR-32 puts a received recipe (PRF-08 AC6). */
sealed interface TakeOverTarget {
    /** A collection tea the user chose or the match preselected. */
    data class Existing(val teaId: String) : TakeOverTarget

    /**
     * *Neuen Tee anlegen*: from the local catalog by [catalogKey] (`AddFromCatalog`, without a package), or without
     * catalog ([catalogKey] null) with the file's name, Kategorie and Sorte.
     */
    data class NewTea(val catalogKey: String?) : TakeOverTarget
}

/** How [RecipeTakeOver.takeOver] ended. */
sealed interface TakeOverResult {
    /** The recipe [recipeName] is the tea [teaId]'s newest; SCR-09 opens with „Rezept „…" übernommen". */
    data class TakenOver(val teaId: String, val recipeName: String) : TakeOverResult

    /** The tea has 10 recipes already („Höchstens 10 Rezepte"); nothing was written. */
    data object LimitReached : TakeOverResult

    /** The database failed (or the catalog tea is gone); the recipe was not added. */
    data object Failed : TakeOverResult
}

/**
 * *Rezept übernehmen* (PRF-08 AC6, AC7, 05 §8a): the only write of a received recipe. It adds a **new** recipe,
 * never the default, at the end of the tea's recipes, with *Kein Gefäß* (the file names only a vessel type) and the
 * file's leaf, water and steps, named as in the file or with „ (2)" ([RecipeMatch.recipeName]). A new tea is created
 * first ([TakeOverTarget.NewTea]); the recipe's save is one transaction of its own (`RecipeRepository.saveRecipe`).
 */
class RecipeTakeOver @Inject constructor(
    private val recipes: RecipeRepository,
    private val addFromCatalog: AddFromCatalog,
    private val addWithoutCatalog: AddWithoutCatalog,
    private val sorteNames: SorteNames,
) {
    suspend fun takeOver(recipe: SharedRecipe, target: TakeOverTarget): TakeOverResult {
        val teaId = when (target) {
            is TakeOverTarget.Existing -> target.teaId
            is TakeOverTarget.NewTea -> newTea(recipe.tea, target.catalogKey) ?: return TakeOverResult.Failed
        }
        val existing = recipes.observeRecipes(teaId).first()
        if (existing.size >= Recipe.MAX_PER_TEA) return TakeOverResult.LimitReached
        val name = RecipeMatch.recipeName(recipe.name, existing.map { it.name })
        val added = Recipe(
            id = UUID.randomUUID().toString(),
            teaId = teaId,
            name = name,
            method = recipe.method,
            leafGrams = recipe.leafGrams,
            waterMl = recipe.waterMl,
            baseTempC = recipe.baseTempC,
            steps = recipe.steps,
            vesselId = null,
            preAlertSeconds = recipe.preAlertSeconds,
            isDefault = false,
            position = (existing.maxOfOrNull { it.position } ?: -1) + 1,
            source = RecipeSource.USER,
        )
        return when (recipes.saveRecipe(added)) {
            SaveRecipeResult.Saved -> TakeOverResult.TakenOver(teaId, name)
            SaveRecipeResult.LimitReached -> TakeOverResult.LimitReached
        }
    }

    private suspend fun newTea(file: SharedTea, catalogKey: String?): String? = if (catalogKey != null) {
        when (val result = addFromCatalog.addWithoutPackage(catalogKey)) {
            is AddFromCatalogResult.Added -> result.teaId
            is AddFromCatalogResult.Repurchased -> result.teaId
            AddFromCatalogResult.NotInCatalog, AddFromCatalogResult.Failed -> null
        }
    } else {
        when (val result = addWithoutCatalog.add(withoutCatalog(file), purchase = null)) {
            is AddWithoutCatalogResult.Added -> result.teaId
            AddWithoutCatalogResult.Failed -> null
        }
    }

    /** SCR-11's tea without catalog from the file: its name, secondary name, Kategorie and Sorte (05 §8a). */
    private fun withoutCatalog(file: SharedTea): Tea {
        val style = file.styleKey?.let(BuiltInStyles::byKey)
            ?: BuiltInStyles.all.firstOrNull { it.category == file.category && it.key.endsWith(GENERIC_SUFFIX) }
        return Tea(
            id = "",
            name = file.name,
            secondaryName = file.secondaryName,
            category = file.category,
            form = if (style?.recipes?.all { it.method in WHISKED } == true) TeaForm.POWDER else TeaForm.LOOSE_LEAF,
            styleKey = style?.key,
            teaType = style?.key?.let(sorteNames::name),
        )
    }

    private companion object {
        const val GENERIC_SUFFIX = "_generic"
        val WHISKED = setOf(BrewMethod.MATCHA_USUCHA, BrewMethod.MATCHA_KOICHA)
    }
}
