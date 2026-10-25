package com.umamit.core.catalog.parser

import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.TeaForm
import com.umamit.core.model.UmamiLevel

/**
 * A catalog tea's style (CAT-06) and derived recipes (CAT-04, CAT-05), as the refresh stores
 * them (05 §4.1 `style_key`, `derived_recipes_json`, `recipe_check_reason`).
 */
data class CatalogRecipes(
    val styleKey: String,
    val form: TeaForm,
    val umamiLevel: UmamiLevel,
    val recipes: List<RecipeTemplate>,
    val checkReason: RecipeCheckReason?,
) {
    companion object {
        private val JAPAN = Regex("japan", RegexOption.IGNORE_CASE)

        /** Maps [page] to its style and derives its recipes from its grid, prose and cold-brew badge. */
        fun derive(page: ProductPage): CatalogRecipes {
            val mapped = StyleMapper.map(page.categoryPath, page.name, page.teaType, page.isFlavored)
            // A tea from Japan in a generic section is Japanese too (its Kyusu row is the default).
            val match = mapped.copy(
                japanese =
                mapped.japanese || page.originCountry?.let(JAPAN::containsMatchIn) == true,
            )
            val derived = RecipeDeriver.derive(BrewInfo(page.brewTable, page.brewText, page.coldBrewSuitable), match)
            return CatalogRecipes(
                styleKey = match.style.key,
                form = match.form,
                umamiLevel = StyleMapper.umamiLevel(page.tasteScales, match.style),
                recipes = derived.recipes,
                checkReason = derived.checkReason,
            )
        }
    }
}
