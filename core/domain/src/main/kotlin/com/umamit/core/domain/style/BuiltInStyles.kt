package com.umamit.core.domain.style

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.UmamiLevel

/**
 * A built-in tea style (STY-01, 08 §3): the mapping target of catalog teas (CAT-06) with its
 * umami level, freshness window, teaspoon weight and fallback recipes.
 *
 * @property key the style key stored with a tea (`sencha`, `green_generic`, …).
 * @property freshnessDays suggested days to drink the tea after opening.
 * @property gramsPerTeaspoon converts teaspoon amounts of the shop's prose (08 §2.5).
 * @property recipes the style's recipes in 08 §3's order; the first is the default.
 * @property japanese a Japanese tea: its Kyusu row is its default recipe (08 §2.5 step 1).
 */
data class TeaStyle(
    val key: String,
    val umami: UmamiLevel,
    val freshnessDays: Int,
    val gramsPerTeaspoon: Double,
    val recipes: List<RecipeTemplate>,
    val japanese: Boolean,
) {
    /** The hot recipes: neither cold nor ice brew. */
    val hotRecipes: List<RecipeTemplate> get() = recipes.filter { it.method !in COLD_METHODS }

    /** The style's default recipe, always a hot one. */
    val defaultRecipe: RecipeTemplate get() = recipes.first { it.isDefault }

    /** The style's own cold brew, if it has one (only gyokuro and sencha do). */
    val coldBrew: RecipeTemplate? get() = recipes.firstOrNull { it.method == BrewMethod.COLD_BREW }

    /** The style's cold brew, else sencha's (08 §2.5 step 6, D-076). */
    val coldBrewOrFallback: RecipeTemplate get() = coldBrew ?: requireNotNull(BuiltInStyles.byKey("sencha")?.coldBrew)

    private companion object {
        val COLD_METHODS = setOf(BrewMethod.COLD_BREW, BrewMethod.ICE_BREW)
    }
}

/**
 * The built-in green tea styles of 08 §3 (STY-01, D-076). Temperatures in 5 °C steps; a step
 * without a temperature brews at the recipe's base temperature. The generic styles of other
 * tea categories (white, oolong, …) come with the collection editor (M3).
 */
object BuiltInStyles {
    val all: List<TeaStyle> = listOf(
        japanese(
            "gyokuro",
            UmamiLevel.VERY_HIGH,
            60,
            3.0,
            kyusu(6.0, 60, 50, 120 at null, 20 at 55, 60 at 65, 90 at 75),
            coldBrew(10.0, 500, hours = 3),
            RecipeTemplate("Eisaufguss", BrewMethod.ICE_BREW, 6.0, 100, COLD_C, listOf(infusion(2 * HOUR))),
        ),
        japanese("kabusecha", UmamiLevel.HIGH, 45, 2.5, kyusu(5.0, 120, 65, 90 at null, 20 at 70, 60 at 80)),
        japanese(
            "sencha",
            UmamiLevel.MEDIUM,
            45,
            2.5,
            kyusu(5.0, 150, 75, 60 at null, 15 at 80, 45 at 85),
            coldBrew(10.0, 1000, hours = 4),
        ),
        japanese("fukamushi_sencha", UmamiLevel.MEDIUM, 45, 3.0, kyusu(5.0, 150, 70, 45 at null, 5 at 75, 30 at 80)),
        japanese("shincha", UmamiLevel.HIGH, 30, 2.5, kyusu(5.0, 150, 70, 60 at null, 15 at 75, 45 at 80)),
        japanese(
            "matcha",
            UmamiLevel.HIGH,
            30,
            2.0,
            RecipeTemplate("Usucha", BrewMethod.MATCHA_USUCHA, 2.0, 70, 75, listOf(RecipeStep(StepKind.WHISK, 20))),
            RecipeTemplate("Koicha", BrewMethod.MATCHA_KOICHA, 4.0, 35, 75, listOf(RecipeStep(StepKind.WHISK, 40))),
        ),
        japanese("karigane", UmamiLevel.MEDIUM, 45, 1.5, kyusu(5.0, 150, 70, 60 at null, 20 at 75, 60 at 85)),
        japanese("bancha", UmamiLevel.LOW, 60, 2.0, kyusu(5.0, 200, 85, 45 at null, 20 at 90, 60 at 95)),
        japanese("hojicha", UmamiLevel.LOW, 60, 1.5, kyusu(4.0, 200, 95, 30 at null, 30 at null, 60 at null)),
        // A powder is whisked like a matcha, never steeped and never cold-brewed (D-091).
        japanese(
            "hojicha_powder",
            UmamiLevel.LOW,
            30,
            2.0,
            RecipeTemplate("Usucha", BrewMethod.MATCHA_USUCHA, 2.0, 70, 80, listOf(RecipeStep(StepKind.WHISK, 20))),
        ),
        japanese("genmaicha", UmamiLevel.LOW, 45, 3.0, kyusu(5.0, 200, 85, 45 at null, 15 at 90, 60 at 95)),
        japanese("kamairicha", UmamiLevel.LOW, 60, 2.5, kyusu(5.0, 150, 80, 60 at null, 20 at 85, 45 at 90)),
        japanese("tamaryokucha", UmamiLevel.MEDIUM, 45, 2.5, kyusu(5.0, 150, 75, 60 at null, 15 at 80, 45 at 85)),
        other(
            "longjing",
            UmamiLevel.MEDIUM,
            90,
            1.5,
            gongfu(5.0, 80, 20, 25, 30, 40, 60),
            western(3.0, 80, 120, 150),
        ),
        other("anji_bai_cha", UmamiLevel.HIGH, 90, 1.0, gongfu(4.0, 80, 20, 25, 30, 40, 60)),
        other("bi_luo_chun", UmamiLevel.MEDIUM, 90, 2.0, gongfu(4.0, 75, 15, 20, 25, 35, 50)),
        other("korean_green", UmamiLevel.HIGH, 60, 2.0, kyusu(4.0, 120, 70, 60 at null, 30 at 75, 60 at 80)),
        other("flavored_green", UmamiLevel.LOW, 120, 2.5, western(3.0, 80, 150, 180)),
        other(
            "green_generic",
            UmamiLevel.MEDIUM,
            90,
            2.5,
            western(3.0, 80, 120, 150),
            gongfu(5.0, 80, 15, 20, 25, 35, 50),
        ),
    )

    private val byKey: Map<String, TeaStyle> = all.associateBy { it.key }

    /** The fallback for green teas the mapping doesn't know (CAT-06). */
    val GENERIC: TeaStyle = requireNotNull(byKey["green_generic"])

    /** The style with [key], or null. */
    fun byKey(key: String): TeaStyle? = byKey[key]
}

private const val HOUR = 3600
private const val COLD_C = 5

/** A step's duration in seconds and its temperature (null: the base temperature). */
private data class Timed(val seconds: Int, val tempC: Int?)

private infix fun Int.at(tempC: Int?) = Timed(this, tempC)

private fun infusion(seconds: Int, tempC: Int? = null) = RecipeStep(StepKind.INFUSION, seconds, tempC)

private fun kyusu(leaf: Double, water: Int, temp: Int, vararg steps: Timed) = RecipeTemplate(
    name = "Kyusu",
    method = BrewMethod.KYUSU,
    leafGrams = leaf,
    waterMl = water,
    baseTempC = temp,
    steps = steps.map { infusion(it.seconds, it.tempC) },
)

private const val GONGFU_ML = 100
private const val MUG_ML = 250

private fun gongfu(leaf: Double, temp: Int, vararg seconds: Int) =
    RecipeTemplate("Gongfu", BrewMethod.GONGFU, leaf, GONGFU_ML, temp, seconds.map { infusion(it) })

private fun western(leaf: Double, temp: Int, vararg seconds: Int) =
    RecipeTemplate("Westlich", BrewMethod.WESTERN, leaf, MUG_ML, temp, seconds.map { infusion(it) })

private fun coldBrew(leaf: Double, water: Int, hours: Int) =
    RecipeTemplate("Kaltaufguss", BrewMethod.COLD_BREW, leaf, water, COLD_C, listOf(infusion(hours * HOUR)))

@Suppress("LongParameterList")
private fun style(
    key: String,
    umami: UmamiLevel,
    freshness: Int,
    teaspoon: Double,
    japanese: Boolean,
    recipes: Array<out RecipeTemplate>,
) = TeaStyle(
    key = key,
    umami = umami,
    freshnessDays = freshness,
    gramsPerTeaspoon = teaspoon,
    recipes = recipes.mapIndexed { index, recipe ->
        recipe.copy(source = RecipeSource.STYLE_DEFAULT, isDefault = index == 0)
    },
    japanese = japanese,
)

private fun japanese(key: String, umami: UmamiLevel, freshness: Int, teaspoon: Double, vararg recipes: RecipeTemplate) =
    style(key, umami, freshness, teaspoon, japanese = true, recipes = recipes)

private fun other(key: String, umami: UmamiLevel, freshness: Int, teaspoon: Double, vararg recipes: RecipeTemplate) =
    style(key, umami, freshness, teaspoon, japanese = false, recipes = recipes)
