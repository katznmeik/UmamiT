package com.umamit.core.model

/**
 * A recipe no tea owns yet: a built-in style's default (STY-01, 08 §3) or a catalog tea's
 * derived recipe (CAT-04, 08 §2.5). It has a [Recipe]'s values (05 §3.2, §3.3) without the
 * tea, the vessel and the pre-alert; adding the tea to the collection makes it a [Recipe].
 *
 * @property source where its values came from; for a derived recipe the least certain of its
 *   fields (style default before text before table).
 * @property isDefault the tea's default recipe (one per tea).
 */
data class RecipeTemplate(
    val name: String,
    val method: BrewMethod,
    val leafGrams: Double,
    val waterMl: Int,
    val baseTempC: Int,
    val steps: List<RecipeStep>,
    val source: RecipeSource = RecipeSource.STYLE_DEFAULT,
    val isDefault: Boolean = false,
) {
    /** This template as the recipe [id] of the collection tea [teaId], at [position] in its list. */
    fun toRecipe(id: String, teaId: String, position: Int): Recipe = Recipe(
        id = id,
        teaId = teaId,
        name = name,
        method = method,
        leafGrams = leafGrams,
        waterMl = waterMl,
        baseTempC = baseTempC,
        steps = steps,
        isDefault = isDefault,
        position = position,
        source = source,
    )
}
