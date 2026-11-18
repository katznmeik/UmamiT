package com.umamit.core.backup.recipe

import com.umamit.core.model.BrewMethod
import com.umamit.core.model.StepKind
import com.umamit.core.model.VesselType
import java.util.Locale

/**
 * The words and templates of the share text, from `strings.xml` ([ResourceRecipeShareLabels]); format templates
 * take `String.format` arguments.
 */
interface RecipeShareLabels {
    fun method(method: BrewMethod): String

    fun stepKind(kind: StepKind): String

    fun vesselType(type: VesselType): String

    fun template(part: RecipeSharePart): String
}

/** The templates of [RecipeShareLabels.template]. */
enum class RecipeSharePart {
    /** „%1$s – Rezept „%2$s"": tea, recipe. */
    TITLE,

    /** „%1$s (%2$s) – Rezept „%3$s"": tea, secondary name, recipe. */
    TITLE_WITH_SECONDARY,

    /** „ · " between a line's parts. */
    SEPARATOR,

    /** „%1$s auf %2$s": leaf, water. */
    AMOUNTS,

    /** „Gefäß: %1$s": the vessel type. */
    VESSEL,

    /** „%1$s g": the leaf as text. */
    GRAMS,

    /** „%1$d ml". */
    MILLILITRES,

    /** „%1$d °C". */
    CELSIUS,

    /** „%1$d. %2$s": the step's number and kind. */
    STEP,

    /** „Geteilt aus UmamiT – …". */
    FOOTER,
}

/**
 * The readable share text of a [SharedRecipe] (PRF-08 AC1, the example in 03 SCR-12), with 08's and 05 §7's number
 * rules: the tea (and its secondary name) with the recipe's name; method, leaf, water, base temperature and the
 * vessel type if there is one; one line per step with its temperature (its own, else the base) and its duration
 * („0:10", „1:30", „4:00:00"); the shop link on a line of its own for a catalog tea; „Geteilt aus UmamiT …".
 */
object RecipeShareText {
    private val GERMAN = Locale.GERMANY
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3_600

    fun build(recipe: SharedRecipe, labels: RecipeShareLabels): String {
        fun format(part: RecipeSharePart, vararg args: Any): String = labels.template(part).format(GERMAN, *args)
        val separator = labels.template(RecipeSharePart.SEPARATOR)
        val tea = recipe.tea
        val title = tea.secondaryName
            ?.let { format(RecipeSharePart.TITLE_WITH_SECONDARY, tea.name, it, recipe.name) }
            ?: format(RecipeSharePart.TITLE, tea.name, recipe.name)
        val summary = listOfNotNull(
            labels.method(recipe.method),
            format(
                RecipeSharePart.AMOUNTS,
                format(RecipeSharePart.GRAMS, "%.1f".format(GERMAN, recipe.leafGrams)),
                format(RecipeSharePart.MILLILITRES, recipe.waterMl),
            ),
            format(RecipeSharePart.CELSIUS, recipe.baseTempC),
            recipe.vesselType?.let { format(RecipeSharePart.VESSEL, labels.vesselType(it)) },
        ).joinToString(separator)
        val steps = recipe.steps.mapIndexed { index, step ->
            listOf(
                format(RecipeSharePart.STEP, index + 1, labels.stepKind(step.kind)),
                format(RecipeSharePart.CELSIUS, step.tempC ?: recipe.baseTempC),
                clock(step.durationSeconds),
            ).joinToString(separator)
        }
        return (listOf(title, summary) + steps + listOfNotNull(tea.shopUrl, labels.template(RecipeSharePart.FOOTER)))
            .joinToString("\n")
    }

    /** „0:10", „1:30", „4:00:00" (05 §7). */
    internal fun clock(seconds: Int): String {
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val rest = seconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            "%d:%02d:%02d".format(GERMAN, hours, minutes, rest)
        } else {
            "%d:%02d".format(GERMAN, minutes, rest)
        }
    }
}
