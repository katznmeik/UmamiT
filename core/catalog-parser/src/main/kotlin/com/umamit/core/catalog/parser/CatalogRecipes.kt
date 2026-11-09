package com.umamit.core.catalog.parser

import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel

/**
 * A catalog tea's style (CAT-06) and derived recipes (CAT-04, CAT-05), as the refresh stores
 * them (05 §4.1 `style_key`, `derived_recipes_json`, `recipe_check_reason`), and the tea's [category]: its
 * style's, a chai's its base tea's (#206), as a collection copy gets it.
 */
data class CatalogRecipes(
    val styleKey: String,
    val form: TeaForm,
    val umamiLevel: UmamiLevel,
    val recipes: List<RecipeTemplate>,
    val checkReason: RecipeCheckReason?,
    val category: TeaCategory,
) {
    companion object {
        private val JAPAN = Regex("japan", RegexOption.IGNORE_CASE)

        /** „20 Aufgussbeutel", „15 Teebeutel" in the name: the package's bags. */
        private val BAG_COUNT =
            Regex("""(\d+)\s*(?:x\s*)?(?:aufguss|tee|pyramiden|kaltaufguss)?beutel""", RegexOption.IGNORE_CASE)
        private val GRAMS_PER_BAG = 0.5..30.0

        /** A bag's weight (D-089): the package's grams over the bags its name counts; null when either is missing. */
        private fun gramsPerBag(page: ProductPage): Double? {
            val bags = page.name?.let { BAG_COUNT.find(it) }?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
            val grams = page.packageGrams ?: return null
            return bags?.let { grams / it }?.takeIf { it in GRAMS_PER_BAG }
        }

        /** Maps [page] to its style and derives its recipes from its grid, prose and cold-brew badge. */
        fun derive(page: ProductPage): CatalogRecipes {
            // A tea of another section by its section's crumb, though it is also filed under a format (D-108).
            val path = if (page.scope == CatalogScope.GREEN) page.categoryPath else TeaSections.pathIn(page.breadcrumb)
            val mapped = StyleMapper.map(
                path ?: page.categoryPath,
                page.name,
                page.teaType,
                page.isFlavored,
                page.originCountry,
                page.ingredients,
            )
            // A tea from Japan in a generic section is Japanese too (its Kyusu row is the default).
            val match = mapped.copy(
                japanese =
                mapped.japanese || page.originCountry?.let(JAPAN::containsMatchIn) == true,
            )
            val info = BrewInfo(page.brewTable, page.brewText, page.coldBrewSuitable, gramsPerBag(page))
            val derived = RecipeDeriver.derive(info, match)
            return CatalogRecipes(
                styleKey = match.style.key,
                form = match.form,
                umamiLevel = StyleMapper.umamiLevel(page.tasteScales, match.style),
                recipes = derived.recipes,
                checkReason = derived.checkReason,
                category = match.category,
            )
        }
    }
}
