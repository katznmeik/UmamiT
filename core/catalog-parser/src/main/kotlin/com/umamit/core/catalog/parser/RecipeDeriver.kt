package com.umamit.core.catalog.parser

import com.umamit.core.common.snap5
import com.umamit.core.domain.style.TeaStyle
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewTableRow
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A tea's brewing information as the shop shows it (08 §2.3): the grid's rows, the description's
 * brewing prose and the „Coldbrew geeignet" badge; [gramsPerBag] weighs a tea bag („1Stk./500ml")
 * when the package says it (D-089), else null.
 */
data class BrewInfo(
    val grid: List<BrewGridRow>,
    val prose: String?,
    val coldBrewBadge: Boolean,
    val gramsPerBag: Double? = null,
)

/**
 * A tea's derived recipes (CAT-04), the default first, and why the tea's recipe should be
 * checked (CAT-05), judged by its default recipe only; null when it came straight from the grid.
 */
data class DerivedRecipes(val recipes: List<RecipeTemplate>, val checkReason: RecipeCheckReason?) {
    val default: RecipeTemplate get() = recipes.first { it.isDefault }
}

/**
 * Turns the shop's brewing grid and prose into timer recipes (CAT-04, 08 §2.5): one recipe per
 * grid row, each field from the grid, else the prose, else the style's default; ranges at their
 * midpoint, temperatures in 5 °C steps, times in 5 s steps (whole minutes from 10 min, PRF-04);
 * a cold brew when the badge or the prose asks for one. Never adds a rinse.
 */
object RecipeDeriver {
    /** The reasons in the order the tea names one (D-082): what most needs checking first. */
    private val PRIORITY = listOf(
        RecipeCheckReason.IMPLAUSIBLE_VALUE,
        RecipeCheckReason.TABLE_TEXT_CONFLICT,
        RecipeCheckReason.FROM_STYLE_DEFAULT,
        RecipeCheckReason.FROM_TEXT,
        RecipeCheckReason.UNMAPPED_STYLE,
    )

    fun derive(info: BrewInfo, match: StyleMatch): DerivedRecipes {
        val style = match.style
        val prose = Prose.read(info.prose, style.gramsPerTeaspoon, info.gramsPerBag)
        // A whisked powder (matcha, hōjicha powder, D-091): its rows become one Usucha recipe.
        val matcha = style.defaultRecipe.method == BrewMethod.MATCHA_USUCHA
        // A row at „kalt" is the cold brew's (D-089), never a hot recipe.
        val (coldRows, hotRows) = info.grid.mapNotNull {
            GridRow.read(it, matcha, style.gramsPerTeaspoon, info.gramsPerBag)
        }
            .partition { it.cold }
        val rows = if (matcha) hotRows.take(1) else hotRows
        val hot: List<Derived> = if (rows.isEmpty()) {
            listOf(HotRecipe(style.defaultRecipe.method, style.defaultRecipe.name, null, rows, prose, style).derive()) +
                style.hotRecipes.drop(1).map { Derived(it, emptySet()) }
        } else {
            rows.map { HotRecipe(it.method, it.name, it, rows, prose, style).derive() }
        }
        // A Japanese tea's Kyusu row, else the row of the style's default method (Gongfu for the
        // Chinese styles, D-089), else the first.
        val hotDefault = if (match.japanese) {
            hot.indexOfFirst { it.recipe.method == BrewMethod.KYUSU || it.recipe.method == BrewMethod.MATCHA_USUCHA }
        } else {
            hot.indexOfFirst { it.recipe.method == style.defaultRecipe.method }
        }.coerceAtLeast(0)
        // A powder is shaken cold, never steeped for hours: no cold brew (08 §3's matcha, D-089).
        val coldRow = coldRows.firstOrNull()
        // A Kräutertee's style has no cold brew (08 §3, D-093): only the shop's own cold values add one.
        val badge = info.coldBrewBadge && style.category == TeaCategory.GREEN
        val wantsCold = badge || prose.cold != null || match.coldBrewDefault || coldRow != null
        val cold = if (wantsCold && match.form != TeaForm.POWDER) {
            coldBrew(coldRow, prose.cold, style, hot[hotDefault].recipe)
        } else {
            null
        }
        val all = hot + listOfNotNull(cold)
        val defaultIndex = if (match.coldBrewDefault && cold != null) all.lastIndex else hotDefault
        val default = all[defaultIndex]
        val ordered = listOf(default) + all.filterIndexed { index, _ -> index != defaultIndex }
        val reasons = default.reasons + listOfNotNull(RecipeCheckReason.UNMAPPED_STYLE.takeIf { match.unmapped })
        return DerivedRecipes(
            recipes = ordered.map { it.recipe.copy(isDefault = it === default) },
            checkReason = PRIORITY.firstOrNull { it in reasons },
        )
    }

    /**
     * The cold brew (08 §2.5 step 6): the grid's row at „kalt", else the prose's values, else the
     * style's cold brew (sencha's for styles without). Bags of unknown weight brew the style's
     * ratio at their stated water (D-089).
     */
    private fun coldBrew(row: GridRow?, prose: ColdProse?, style: TeaStyle, hotDefault: RecipeTemplate): Derived {
        val template = style.coldBrewOrFallback
        val fields = Fields()
        val styleSeconds = template.steps.first().durationSeconds.toDouble()
        val given = row?.infusions.orEmpty()
        var seconds = given.firstOrNull()?.let { fields.table(it) }
            ?: prose?.seconds?.let { fields.text(it) }
            ?: fields.style(styleSeconds)
        if (seconds !in COLD_SECONDS) seconds = fields.implausible(styleSeconds)
        var (leaf, water) = coldAmount(row, prose, template, hotDefault, fields)
        if (leaf / water * PER_100 !in RATIO) {
            fields.implausible(Unit)
            leaf = template.leafGrams
            water = template.waterMl.toDouble()
        }
        val recipe = RecipeTemplate(
            name = template.name,
            method = BrewMethod.COLD_BREW,
            leafGrams = leaf(leaf),
            waterMl = water(water),
            baseTempC = COLD_C,
            steps = (listOf(seconds) + given.drop(1)).map { RecipeStep(StepKind.INFUSION, duration(it)) },
            source = fields.source,
        )
        return Derived(recipe, fields.reasons)
    }

    /** The cold brew's leaf and water: the „kalt" row's, else the prose's, else the style's (step 6). */
    private fun coldAmount(
        row: GridRow?,
        prose: ColdProse?,
        template: RecipeTemplate,
        hotDefault: RecipeTemplate,
        fields: Fields,
    ): Pair<Double, Double> {
        val tableAmount = row?.amount
        val proseAmount = prose?.amount
        val bagWater = row?.bagWaterMl ?: prose?.bagWaterMl?.takeIf { tableAmount == null }?.also { fields.text(Unit) }
        return when {
            tableAmount != null -> tableAmount.leafGrams to (tableAmount.waterMl ?: LITRE_ML)

            // Bags of unknown weight: the style's ratio at their water (D-089).
            bagWater != null -> fields.style(template.leafGrams / template.waterMl * bagWater to bagWater)

            proseAmount != null -> fields.text(proseAmount.leafGrams to (proseAmount.waterMl ?: LITRE_ML))

            prose?.doubleDose == true -> fields.text(2 * hotDefault.leafGrams to hotDefault.waterMl.toDouble())

            else -> fields.style(template.leafGrams to template.waterMl.toDouble())
        }
    }

    /** One grid row's values (08 §2.5 step 2), columns by their header label, else by position. */
    @Suppress("LongParameterList")
    private class GridRow(
        val method: BrewMethod,
        val name: String,
        val amount: Amount?,
        val temperature: Double?,
        val infusions: List<Double>,
        /** The row's temperature is „kalt": the cold brew's row (D-089). */
        val cold: Boolean,
        /** The water of bags of unknown weight („1Stk./500ml" without grams per bag). */
        val bagWaterMl: Double?,
    ) {
        companion object {
            private val INFUSION_LABEL = Regex("""(\d)\.\s*aufguss""", RegexOption.IGNORE_CASE)
            private val COLD_CELL = Regex("""kalt|cold""", RegexOption.IGNORE_CASE)
            private const val AMOUNT_COLUMN = 0
            private const val TEMPERATURE_COLUMN = 1

            /** The row as a recipe: Kyusu, Westlich or Gongfu, for matcha Usucha; null for another kind. */
            fun read(row: BrewGridRow, matcha: Boolean, gramsPerTeaspoon: Double, gramsPerBag: Double?): GridRow? {
                val (method, name) = when {
                    row.kind !in KINDS -> return null
                    matcha -> BrewMethod.MATCHA_USUCHA to USUCHA
                    row.kind == BrewTableRow.KIND_JAPAN -> BrewMethod.KYUSU to KYUSU
                    row.kind == BrewTableRow.KIND_CHINA -> BrewMethod.GONGFU to GONGFU
                    else -> BrewMethod.WESTERN to WESTERN
                }
                var amount: Amount? = null
                var bags: Bags? = null
                var temperature: Double? = null
                var cold = false
                val infusions = sortedMapOf<Int, Double>()
                row.cells.forEachIndexed { index, cell ->
                    when (val column = column(cell.label.orEmpty().lowercase(), index)) {
                        AMOUNT_COLUMN -> {
                            bags = BrewValues.bags(cell.text)
                            amount = BrewValues.amount(cell.text, gramsPerTeaspoon) ?: bags?.weighed(gramsPerBag)
                        }

                        TEMPERATURE_COLUMN -> {
                            temperature = BrewValues.temperature(cell.text)
                            cold = temperature == null && COLD_CELL.containsMatchIn(cell.text)
                        }

                        null -> Unit

                        else -> BrewValues.seconds(cell.text)?.let { infusions[column] = it }
                    }
                }
                val bagWater = bags?.waterMl?.takeIf { amount == null }
                return GridRow(method, name, amount, temperature, infusions.values.filter { it > 0 }, cold, bagWater)
            }

            /** A cell's column by its header label (the n-th infusion is column n + 1), else by its [index]. */
            private fun column(label: String, index: Int): Int? = when {
                "menge" in label -> AMOUNT_COLUMN
                "temp" in label -> TEMPERATURE_COLUMN
                label.isEmpty() -> index
                else -> INFUSION_LABEL.find(label)?.groupValues?.get(1)?.toInt()?.plus(1)
            }

            private val KINDS = setOf(BrewTableRow.KIND_JAPAN, BrewTableRow.KIND_WESTERN, BrewTableRow.KIND_CHINA)
        }
    }

    /** One hot recipe: a grid row (or none) with its gaps filled from the prose and the style (steps 3–5, 7). */
    private class HotRecipe(
        private val method: BrewMethod,
        private val name: String,
        private val row: GridRow?,
        private val rows: List<GridRow>,
        private val prose: Prose,
        private val style: TeaStyle,
    ) {
        private val template = style.hotRecipes.firstOrNull { it.method == method } ?: style.defaultRecipe
        private val fields = Fields()

        fun derive(): Derived {
            val (leaf, water) = amount()
            val temperature = temperature()
            val steps = if (method == BrewMethod.MATCHA_USUCHA) {
                // The shop states no whisk time: the style's, not flagged (step 5).
                template.steps
            } else {
                steps(temperature)
            }
            val recipe = RecipeTemplate(
                name = name,
                method = method,
                leafGrams = leaf(leaf),
                waterMl = water(water),
                baseTempC = temperature,
                steps = steps,
                source = fields.source,
            )
            return Derived(recipe, fields.reasons)
        }

        private fun amount(): Pair<Double, Double> {
            val table = row?.amount
            val chosen = table?.let { fields.table(it) } ?: prose.amount?.let { fields.text(it) }
            val plausible = when {
                chosen == null -> fields.style(null)
                chosen.gramsPer100Ml !in RATIO -> fields.implausible(null)
                else -> chosen
            }
            if (plausible == null) return template.leafGrams to template.waterMl.toDouble()
            if (table != null) {
                fields.conflictIf(prose.amount, table, rows.mapNotNull { it.amount }) { p, t ->
                    abs(p.gramsPer100Ml - t.gramsPer100Ml) > AMOUNT_TOLERANCE * t.gramsPer100Ml
                }
            }
            val water = plausible.waterMl ?: perLitreVolume()
            return plausible.leafGrams * (water / (plausible.waterMl ?: LITRE_ML)) to water
        }

        /** Per-litre amounts brew a mug (western) or the style's Kyusu volume (step 4). */
        private fun perLitreVolume(): Double = if (method == BrewMethod.WESTERN) {
            MUG_ML
        } else {
            (style.hotRecipes.firstOrNull { it.method == BrewMethod.KYUSU } ?: template).waterMl.toDouble()
        }

        private fun temperature(): Int {
            val table = row?.temperature
            val chosen = table?.let { fields.table(it) } ?: prose.temperature?.let { fields.text(it) }
            val snapped = chosen?.let(::snap5)
            return when {
                snapped == null -> fields.style(template.baseTempC)

                snapped !in HOT_C -> fields.implausible(template.baseTempC)

                else -> snapped.also {
                    if (table != null) {
                        fields.conflictIf(prose.temperature, table, rows.mapNotNull { it.temperature }) { p, t ->
                            abs(snap5(p) - snap5(t)) > TEMPERATURE_TOLERANCE
                        }
                    }
                }
            }
        }

        private fun steps(temperature: Int): List<RecipeStep> {
            val given = row?.infusions.orEmpty()
            val tableFirst = given.firstOrNull()
            var first = tableFirst?.let { fields.table(it) } ?: prose.seconds?.let { fields.text(it) }
            if (first == null) {
                fields.style(Unit)
                return template.steps.map { rebased(it, temperature) }
            }
            if (first !in FIRST_SECONDS) first = fields.implausible(template.steps.first().durationSeconds.toDouble())
            if (tableFirst != null) {
                val firsts = rows.mapNotNull { it.infusions.firstOrNull() }
                fields.conflictIf(prose.seconds, tableFirst, firsts) { p, t ->
                    abs(p - t) >
                        max(TIME_TOLERANCE_S, TIME_TOLERANCE * t)
                }
            }
            return listOf(RecipeStep(StepKind.INFUSION, duration(first))) + followUps(given, first, temperature)
        }

        /** The infusions after the [first] (step 5): the grid's, else by method and style. */
        private fun followUps(given: List<Double>, first: Double, temperature: Int): List<RecipeStep> = when {
            given.size >= 2 -> given.drop(1).map { RecipeStep(StepKind.INFUSION, duration(it)) }

            // PO preference: a second western infusion of a quarter, 5 °C hotter; not flagged.
            // A Kräutertee keeps the one infusion its style has (08 §3, D-093).
            method == BrewMethod.WESTERN && style.category == TeaCategory.GREEN -> listOf(
                RecipeStep(
                    StepKind.INFUSION,
                    duration(first * WESTERN_SECOND_FACTOR),
                    hotter(temperature, WESTERN_SECOND_RISE_C),
                ),
            )

            method == BrewMethod.KYUSU || method == BrewMethod.GONGFU ->
                template.steps.drop(1).also {
                    if (it.isNotEmpty()) fields.style(Unit)
                }.map { rebased(it, temperature) }

            else -> emptyList()
        }

        /** A style step at the recipe's temperature, keeping the style's rise over its own base. */
        private fun rebased(step: RecipeStep, temperature: Int): RecipeStep =
            step.copy(tempC = step.tempC?.let { hotter(temperature, it - template.baseTempC) })
    }

    /** The values read from the brewing prose: hot from paragraphs without a cold brew, cold from those with. */
    private class Prose(val amount: Amount?, val temperature: Double?, val seconds: Double?, val cold: ColdProse?) {
        companion object {
            private val DOUBLE_DOSE = Regex("""verdoppel|doppelte\s+(?:menge|dosierung)""", RegexOption.IGNORE_CASE)

            fun read(text: String?, gramsPerTeaspoon: Double, gramsPerBag: Double?): Prose {
                val paragraphs = text.orEmpty().split(Regex("""\n\s*\n""")).filter { it.isNotBlank() }
                val (cold, hot) = paragraphs.partition { BrewProse.COLD.containsMatchIn(it) }
                val hotText = hot.joinToString("\n")
                val coldText = cold.joinToString("\n")
                fun amount(paragraph: String) = BrewValues.amount(paragraph, gramsPerTeaspoon)
                    ?: BrewValues.bags(paragraph)?.weighed(gramsPerBag)
                return Prose(
                    amount = hot.firstNotNullOfOrNull(::amount),
                    temperature = hot.firstNotNullOfOrNull { BrewValues.temperature(it) },
                    seconds = BrewValues.seconds(hotText, hours = false),
                    cold = coldText.takeIf { it.isNotEmpty() }?.let {
                        val coldAmount = cold.firstNotNullOfOrNull(::amount)
                        ColdProse(
                            amount = coldAmount,
                            bagWaterMl = cold.firstNotNullOfOrNull(BrewValues::bags)?.waterMl?.takeIf {
                                coldAmount ==
                                    null
                            },
                            seconds = BrewValues.seconds(it),
                            doubleDose = DOUBLE_DOSE.containsMatchIn(it),
                        )
                    },
                )
            }
        }
    }

    /** The cold-brew prose's values; [bagWaterMl] the water of bags of unknown weight. */
    private class ColdProse(val amount: Amount?, val bagWaterMl: Double?, val seconds: Double?, val doubleDose: Boolean)

    /** A derived recipe and why it would flag the tea if it were the default. */
    private class Derived(val recipe: RecipeTemplate, val reasons: Set<RecipeCheckReason>)

    /** Where a recipe's fields came from, and what went wrong (08 §2.5 flags). */
    private class Fields {
        private var text = false
        private var style = false
        private var implausible = false
        private var conflict = false

        fun <T> table(value: T): T = value

        fun <T> text(value: T): T = value.also { text = true }

        fun <T> style(value: T): T = value.also { style = true }

        fun <T> implausible(value: T): T = value.also {
            implausible = true
            style = true
        }

        /** A conflict: the prose's [prose] value differs from the grid's [table] and from every other row's. */
        fun <T : Any> conflictIf(prose: T?, table: T, rows: List<T>, differs: (T, T) -> Boolean) {
            if (prose != null && differs(prose, table) && rows.all { differs(prose, it) }) conflict = true
        }

        val source: RecipeSource
            get() = when {
                style -> RecipeSource.STYLE_DEFAULT
                text -> RecipeSource.CATALOG_TEXT
                else -> RecipeSource.CATALOG_TABLE
            }

        val reasons: Set<RecipeCheckReason>
            get() = buildSet {
                if (text) add(RecipeCheckReason.FROM_TEXT)
                if (style) add(RecipeCheckReason.FROM_STYLE_DEFAULT)
                if (implausible) add(RecipeCheckReason.IMPLAUSIBLE_VALUE)
                if (conflict) add(RecipeCheckReason.TABLE_TEXT_CONFLICT)
            }
    }

    private const val KYUSU = "Kyusu"
    private const val WESTERN = "Westlich"
    private const val GONGFU = "Gongfu"
    private const val USUCHA = "Usucha"
    private const val COLD_C = 5
    private const val MAX_C = 100
    private const val MUG_ML = 250.0
    private const val PER_100 = 100.0
    private val HOT_C = 40..100
    private val FIRST_SECONDS = 5.0..600.0
    private val COLD_SECONDS = 60.0..86_400.0
    private val RATIO = 0.5..15.0
    private const val TEMPERATURE_TOLERANCE = 5
    private const val TIME_TOLERANCE_S = 10.0
    private const val TIME_TOLERANCE = 0.2
    private const val AMOUNT_TOLERANCE = 0.2
    private const val WESTERN_SECOND_FACTOR = 0.25
    private const val WESTERN_SECOND_RISE_C = 5
    private const val MIN_STEP_S = 5
    private const val MINUTE_STEPS_FROM_S = 600
    private const val MAX_STEP_S = 86_400
    private const val STEP_S = 5
    private const val MINUTE_S = 60
    private const val MIN_WATER_ML = 10
    private const val MAX_WATER_ML = 3000
    private const val WATER_STEP_ML = 5
    private const val MIN_LEAF_G = 0.1
    private const val MAX_LEAF_G = 100.0

    /** [base] plus [rise], at most boiling; null when that is the base itself. */
    private fun hotter(base: Int, rise: Int): Int? = snap5(base + rise).coerceAtMost(MAX_C).takeIf { it != base }

    /** Seconds on the steps editor's grid (PRF-04): 5 s steps, whole minutes from 10 min, 5 s – 24 h. */
    private fun duration(seconds: Double): Int {
        val step = if (seconds < MINUTE_STEPS_FROM_S) STEP_S else MINUTE_S
        val rounded = BigDecimal(seconds / step).setScale(0, RoundingMode.HALF_UP).toInt() * step
        return rounded.coerceIn(MIN_STEP_S, MAX_STEP_S)
    }

    private fun leaf(grams: Double): Double =
        BigDecimal(grams).setScale(1, RoundingMode.HALF_UP).toDouble().coerceIn(MIN_LEAF_G, MAX_LEAF_G)

    private fun water(ml: Double): Int =
        ((ml / WATER_STEP_ML).roundToInt() * WATER_STEP_ML).coerceIn(MIN_WATER_ML, MAX_WATER_ML)
}
