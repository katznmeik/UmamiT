package com.umamit.core.backup.recipe

import com.umamit.core.catalog.parser.ArticleNumber
import com.umamit.core.catalog.parser.TeaLinkResult
import com.umamit.core.catalog.parser.TeaLinks
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.VesselType

/**
 * One shared recipe (PRF-08, 05 §8a, D-130 item 17): what the recipe file carries and the share text shows, the
 * same model for both. No recipe notes, no vessel (only its [vesselType]), nothing personal beyond the recipe.
 *
 * A [SharedRecipe] that [RecipeFile.read] returns has passed every check of 05 §8a.
 */
data class SharedRecipe(
    val tea: SharedTea,
    val name: String,
    val method: BrewMethod,
    val leafGrams: Double,
    val waterMl: Int,
    val baseTempC: Int,
    val steps: List<RecipeStep>,
    val vesselType: VesselType? = null,
    /** The recipe's *Vorwarnung*, the base B of its steps' *Automatisch* (null *Wie in Einstellungen*, 0 *Aus*). */
    val preAlertSeconds: Int? = null,
) {
    companion object {
        /**
         * The shared form of [recipe] of [tea]; [vesselType] is the type of the recipe's vessel, if it has one
         * (the vessel itself is never shared). The recipe's notes are left out; a step's blank note becomes null.
         */
        fun of(tea: Tea, recipe: Recipe, vesselType: VesselType?): SharedRecipe = SharedRecipe(
            tea = SharedTea.of(tea),
            name = recipe.name,
            method = recipe.method,
            leafGrams = recipe.leafGrams,
            waterMl = recipe.waterMl,
            baseTempC = recipe.baseTempC,
            steps = recipe.steps.map { it.copy(note = it.note?.takeIf(String::isNotBlank)) },
            vesselType = vesselType,
            preAlertSeconds = recipe.preAlertSeconds,
        )
    }
}

/**
 * The tea of a [SharedRecipe]: its name, secondary name, Kategorie and Sorte, and for a catalog tea its
 * [catalogKey] and [shopUrl] (PRF-08 AC2).
 *
 * @property styleKey a built-in style's key (the Sorte); null for none or one this app does not know.
 * @property catalogKey a base article number (05 §4.1), or null for a tea without catalog.
 * @property shopUrl the tea's product page in its checked form (`https://www.tee-kontor-kiel.de/<slug>/<number>`),
 *   only when CAT-17 AC2's check accepts it and it shows [catalogKey]'s article.
 */
data class SharedTea(
    val name: String,
    val category: TeaCategory,
    val secondaryName: String? = null,
    val styleKey: String? = null,
    val catalogKey: String? = null,
    val shopUrl: String? = null,
) {
    companion object {
        fun of(tea: Tea): SharedTea {
            // The sender's own tea keeps its key when only its stored link fails the check (the receiver still
            // matches it by catalog_key, D-130 Q13); a received file drops both (05 §8a).
            val (linkedKey, shopUrl) = catalogLink(tea.catalogKey, tea.productUrl)
            val catalogKey = linkedKey ?: catalogLink(tea.catalogKey, null).first
            return SharedTea(
                name = tea.name,
                category = tea.category,
                secondaryName = tea.secondaryName?.takeIf(String::isNotBlank),
                styleKey = knownStyle(tea.styleKey),
                catalogKey = catalogKey,
                shopUrl = shopUrl,
            )
        }

        /** [key] when it is a built-in style's key, else null (the take-over falls back to the category's). */
        internal fun knownStyle(key: String?): String? = key?.takeIf { BuiltInStyles.byKey(it) != null }

        /**
         * The catalog key and shop link a recipe file may carry (05 §8a): the key only when it is a base article
         * number; the link only with that key, when CAT-17 AC2's check ([TeaLinks.parse]) accepts it and it shows
         * the key's article. A link that fails drops the key too. The link is kept in its checked form.
         */
        internal fun catalogLink(catalogKey: String?, shopUrl: String?): Pair<String?, String?> {
            val key = catalogKey?.takeIf(::isBaseArticle)
            val link = shopUrl?.let { (TeaLinks.parse(it) as? TeaLinkResult.Accepted)?.link }
            return when {
                key == null -> null to null
                shopUrl == null -> key to null
                link != null && link.base == key -> key to link.url
                else -> null to null
            }
        }

        private fun isBaseArticle(key: String): Boolean =
            ArticleNumber.parse(key)?.let { it.base == key && !it.isSet && !it.isSale } ?: false
    }
}
