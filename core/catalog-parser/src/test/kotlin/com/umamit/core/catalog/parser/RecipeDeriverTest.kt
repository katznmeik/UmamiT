package com.umamit.core.catalog.parser

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PlannedStep
import org.junit.Test

/**
 * The recipe derivation of 08 §2.5 (CAT-04, CAT-05): the worked examples' values and one
 * table per rule. Grids and prose are invented (D-016); the examples' values are 08's.
 */
class RecipeDeriverTest {
    private val derived = mutableListOf<DerivedRecipes>()

    private fun cells(vararg texts: String) = texts.mapIndexed { index, text ->
        BrewGridCell(SyntheticShop.BREW_HEADER.getOrNull(index), text)
    }

    private fun kyusu(vararg texts: String) = BrewGridRow("japan", cells(*texts))

    private fun western(vararg texts: String) = BrewGridRow("westlich", cells(*texts))

    private fun derive(
        vararg grid: BrewGridRow,
        prose: String? = null,
        coldBrewBadge: Boolean = false,
        path: String = "/tee/gruener-tee/japan/sencha/",
        name: String = "Morgennebel Sencha",
        isFlavored: Boolean = false,
    ): DerivedRecipes = RecipeDeriver.derive(
        BrewInfo(grid.toList(), prose, coldBrewBadge),
        StyleMapper.map(categoryPath = path, name = name, isFlavored = isFlavored),
    ).also { derived += it }

    private fun infusions(vararg seconds: Int) = seconds.map { RecipeStep(StepKind.INFUSION, it) }

    @Suppress("LongParameterList")
    private fun recipe(
        name: String,
        method: BrewMethod,
        leaf: Double,
        water: Int,
        temp: Int,
        steps: List<RecipeStep>,
        source: RecipeSource = RecipeSource.CATALOG_TABLE,
        isDefault: Boolean = false,
    ) = RecipeTemplate(name, method, leaf, water, temp, steps, source, isDefault)

    // --- 08 §2.5 worked examples (values only) ---------------------------------------------

    @Test
    fun example1GyokuroKyusuFromTheGridColdBrewFromTheStyle() {
        val result = derive(
            kyusu("~4g/100ml", "50-55°C", "~90 Sek", "~30 Sek", "~20 Sek"),
            coldBrewBadge = true,
            path = "/tee/gruener-tee/japan/gyokuro/",
            name = "Bio Uji Gyokuro",
        )

        assertThat(result.recipes).containsExactly(
            recipe("Kyusu", BrewMethod.KYUSU, 4.0, 100, 55, infusions(90, 30, 20), isDefault = true),
            recipe(
                "Kaltaufguss",
                BrewMethod.COLD_BREW,
                10.0,
                500,
                5,
                infusions(3 * 3600),
                source = RecipeSource.STYLE_DEFAULT,
            ),
        ).inOrder()
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun example2SenchaKyusuDefaultWesternWithASecondInfusionColdBrewFromTheStyle() {
        val result = derive(
            western("~10g/L", "~65°C", "~1 Min", "-", "-"),
            kyusu("~4g/200ml", "60-65°C", "60 Sek", "10 Sek", "10 Sek"),
            coldBrewBadge = true,
        )

        assertThat(result.recipes).containsExactly(
            recipe("Kyusu", BrewMethod.KYUSU, 4.0, 200, 65, infusions(60, 10, 10), isDefault = true),
            recipe(
                "Westlich",
                BrewMethod.WESTERN,
                2.5,
                250,
                65,
                listOf(RecipeStep(StepKind.INFUSION, 60), RecipeStep(StepKind.INFUSION, 15, tempC = 70)),
            ),
            recipe(
                "Kaltaufguss",
                BrewMethod.COLD_BREW,
                10.0,
                1000,
                5,
                infusions(4 * 3600),
                source = RecipeSource.STYLE_DEFAULT,
            ),
        ).inOrder()
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun example3MatchaUsuchaWithTheStylesWhiskTime() {
        val result = derive(
            kyusu("~1g/100ml", "~70°C", "-"),
            path = "/tee/gruener-tee/japan/matcha/",
            name = "Bio Premium Matcha",
        )

        assertThat(result.recipes).containsExactly(
            recipe(
                "Usucha",
                BrewMethod.MATCHA_USUCHA,
                1.0,
                100,
                70,
                listOf(RecipeStep(StepKind.WHISK, 20)),
                isDefault = true,
            ),
        )
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun example4FlavouredSenchaColdBrewFromTheProseWithDoubleDose() {
        val result = derive(
            western("~10g/L", "~65-75°C", "~1 Min", "-", "-"),
            kyusu("~4g/200ml", "65-75°C", "60 Sek", "10 Sek", "10 Sek"),
            prose = "Für einen Kaltaufguss beträgt die Ziehzeit 20 - 30 Minuten im Kühlschrank. " +
                "Bitte die Dosierung verdoppeln.",
            path = "/tee/gruener-tee/aromatisierter-gruentee/",
            name = "Bio Yuzu Sencha",
            isFlavored = true,
        )

        assertThat(result.recipes).containsExactly(
            recipe("Kyusu", BrewMethod.KYUSU, 4.0, 200, 70, infusions(60, 10, 10), isDefault = true),
            recipe(
                "Westlich",
                BrewMethod.WESTERN,
                2.5,
                250,
                70,
                listOf(RecipeStep(StepKind.INFUSION, 60), RecipeStep(StepKind.INFUSION, 15, tempC = 75)),
            ),
            recipe(
                "Kaltaufguss",
                BrewMethod.COLD_BREW,
                8.0,
                200,
                5,
                infusions(25 * 60),
                source = RecipeSource.CATALOG_TEXT,
            ),
        ).inOrder()
        assertThat(result.checkReason).isNull()
    }

    // --- Step 2 and 4: cells, ranges and snapping -------------------------------------------

    @Test
    fun temperaturesTakeTheMidpointAndSnapTo5DegreesHalfUp() {
        listOf(
            "50-55°C" to 55,
            "~65°C" to 65,
            "~65-75°C" to 70,
            "60-70°C" to 65,
            "62°C" to 60,
            "63 °C" to 65,
            "~ 80 °C" to 80,
            "67,5°C" to 70,
        ).forEach { (cell, temp) ->
            val default = derive(kyusu("~4g/200ml", cell, "60 Sek", "10 Sek", "10 Sek")).default
            assertWithMessage(cell).that(default.baseTempC).isEqualTo(temp)
        }
    }

    @Test
    fun timesTakeTheMidpointAndSnapTo5Seconds() {
        listOf(
            "~90 Sek" to 90, "60 Sek" to 60, "~1 Min" to 60, "1,5 Min" to 90, "33 Sek" to 35, "32 Sek" to 30,
            "1-2 Min" to 90, "45 Sekunden" to 45, "2 Minuten" to 120,
        ).forEach { (cell, seconds) ->
            val default = derive(kyusu("~4g/200ml", "~65°C", cell, "10 Sek", "10 Sek")).default
            assertWithMessage(cell).that(default.steps.first().durationSeconds).isEqualTo(seconds)
        }
    }

    @Test
    fun amountsKeepTheStatedVolumeAndScalePerLitreAmounts() {
        data class Case(val row: BrewGridRow, val leaf: Double, val water: Int)
        listOf(
            Case(kyusu("~4g/100ml", "~65°C", "60 Sek", "10 Sek", "10 Sek"), 4.0, 100),
            Case(kyusu("2-2,5g/50ml", "~65°C", "60 Sek", "10 Sek", "10 Sek"), 2.3, 50),
            Case(kyusu("~4 g / 200 ml", "~65°C", "60 Sek", "10 Sek", "10 Sek"), 4.0, 200),
            // Per litre: a western recipe brews a mug (250 ml) …
            Case(western("~10g/L", "~65°C", "~1 Min", "~1 Min", "-"), 2.5, 250),
            Case(western("12g/1L", "~65°C", "~1 Min", "~1 Min", "-"), 3.0, 250),
            // … a kyusu recipe the style's volume (sencha: 150 ml).
            Case(kyusu("~10g/L", "~65°C", "60 Sek", "10 Sek", "10 Sek"), 1.5, 150),
        ).forEach { case ->
            val default = derive(case.row).default
            assertWithMessage(case.row.cells.first().text).that(default.leafGrams).isEqualTo(case.leaf)
            assertWithMessage(case.row.cells.first().text).that(default.waterMl).isEqualTo(case.water)
        }
    }

    // --- Step 3: gaps -----------------------------------------------------------------------

    @Test
    fun aGapIsFilledFromTheProseElseFromTheStyleAndFlagged() {
        val grid = kyusu("~4g/200ml", "-", "60 Sek", "10 Sek", "10 Sek")

        val fromText = derive(grid, prose = "Zubereitung: mit Wasser von 70 °C aufgießen.")
        assertThat(fromText.default.baseTempC).isEqualTo(70)
        assertThat(fromText.default.source).isEqualTo(RecipeSource.CATALOG_TEXT)
        assertThat(fromText.checkReason).isEqualTo(RecipeCheckReason.FROM_TEXT)

        // Boiling first, then cooling down: the brewing temperature is the one below boiling.
        val boilThenCool =
            derive(grid, prose = "Zubereitung: Wasser auf 100 °C aufkochen und auf 70 °C abkühlen lassen.")
        assertThat(boilThenCool.default.baseTempC).isEqualTo(70)

        val fromStyle = derive(grid)
        assertThat(fromStyle.default.baseTempC).isEqualTo(75) // sencha
        assertThat(fromStyle.default.source).isEqualTo(RecipeSource.STYLE_DEFAULT)
        assertThat(fromStyle.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    @Test
    fun withoutGridTheProseAndTheStyleMakeTheRecipes() {
        val fromText = derive(prose = "Zubereitung: 4 g auf 200 ml bei 70 °C für 45 Sekunden ziehen lassen.")
        assertThat(fromText.default).isEqualTo(
            recipe(
                "Kyusu",
                BrewMethod.KYUSU,
                4.0,
                200,
                70,
                listOf(
                    RecipeStep(StepKind.INFUSION, 45),
                    // Follow-ups from the style (sencha: +5 °C, +10 °C over its base).
                    RecipeStep(StepKind.INFUSION, 15, tempC = 75),
                    RecipeStep(StepKind.INFUSION, 45, tempC = 80),
                ),
                source = RecipeSource.STYLE_DEFAULT,
                isDefault = true,
            ),
        )
        assertThat(fromText.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)

        val teaspoons = derive(prose = "Zubereitung: 2 TL auf 200 ml bei 70 °C, 1 Minute ziehen lassen.")
        assertThat(teaspoons.default.leafGrams).isEqualTo(5.0) // sencha: 2,5 g per teaspoon

        val nothing = derive(path = "/tee/gruener-tee/china/", name = "Wolkenspitze")
        assertThat(nothing.recipes.map { it.method }).containsExactly(BrewMethod.WESTERN, BrewMethod.GONGFU).inOrder()
        assertThat(nothing.recipes.all { it.source == RecipeSource.STYLE_DEFAULT }).isTrue()
        assertThat(nothing.default.method).isEqualTo(BrewMethod.WESTERN)
        assertThat(nothing.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    // --- Step 5: steps ----------------------------------------------------------------------

    @Test
    fun aWesternRecipeWithOneInfusionGetsAQuarterAt5DegreesMoreUnflagged() {
        listOf(
            "~1 Min" to 15,
            "~2 Min" to 30,
            "3 Min" to 45,
            "~10 Sek" to 5,
            "50 Sek" to 15,
        ).forEach { (first, second) ->
            val result =
                derive(
                    western("~10g/L", "~80°C", first, "-", "-"),
                    path = "/tee/gruener-tee/china/",
                    name = "Long Jing",
                )
            val steps = result.default.steps
            assertWithMessage(first).that(steps).hasSize(2)
            assertWithMessage(first).that(steps[1]).isEqualTo(RecipeStep(StepKind.INFUSION, second, tempC = 85))
            assertWithMessage(first).that(result.checkReason).isNull()
        }
        // Never hotter than boiling.
        val boiling =
            derive(western("~10g/L", "100°C", "~1 Min", "-", "-"), path = "/tee/gruener-tee/china/", name = "Long Jing")
        assertThat(boiling.default.steps[1].tempC).isNull()
    }

    @Test
    fun aKyusuRecipeWithOnlyTheFirstInfusionTakesTheFollowUpsFromTheStyle() {
        val result = derive(kyusu("~4g/200ml", "~60°C", "60 Sek", "-", "-"))

        assertThat(result.default.steps).containsExactly(
            RecipeStep(StepKind.INFUSION, 60),
            RecipeStep(StepKind.INFUSION, 15, tempC = 65),
            RecipeStep(StepKind.INFUSION, 45, tempC = 70),
        ).inOrder()
        assertThat(result.default.source).isEqualTo(RecipeSource.STYLE_DEFAULT)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    @Test
    fun theGivenInfusionsAreTakenAndARinseIsNeverAdded() {
        val two = derive(kyusu("~4g/200ml", "~60°C", "60 Sek", "20 Sek", "-"))
        assertThat(two.default.steps).containsExactly(*infusions(60, 20).toTypedArray()).inOrder()
        assertThat(two.checkReason).isNull()

        assertThat(derived.flatMap { it.recipes }.flatMap { it.steps }.none { it.kind == StepKind.RINSE }).isTrue()
    }

    // --- Step 1: the default recipe ---------------------------------------------------------

    @Test
    fun theKyusuRowIsTheDefaultOfAJapaneseTeaElseTheFirstRow() {
        val japanese =
            derive(western("~10g/L", "~65°C", "~1 Min"), kyusu("~4g/200ml", "~60°C", "60 Sek", "10 Sek", "10 Sek"))
        assertThat(japanese.default.method).isEqualTo(BrewMethod.KYUSU)
        assertThat(japanese.recipes.first()).isEqualTo(japanese.default)

        val chinese = derive(
            western("~10g/L", "~80°C", "~2 Min", "~3 Min"),
            kyusu("~4g/100ml", "~80°C", "30 Sek", "30 Sek", "40 Sek"),
            path = "/tee/gruener-tee/china/",
            name = "Long Jing",
        )
        assertThat(chinese.default.method).isEqualTo(BrewMethod.WESTERN)
        assertThat(chinese.recipes.single { it.isDefault }.method).isEqualTo(BrewMethod.WESTERN)
    }

    // --- Step 6: cold brew -----------------------------------------------------------------

    @Test
    fun aColdBrewComesFromTheBadgeOrTheProse() {
        val grid = kyusu("~4g/200ml", "~60°C", "60 Sek", "10 Sek", "10 Sek")
        assertThat(derive(grid).recipes.none { it.method == BrewMethod.COLD_BREW }).isTrue()
        data class Case(
            val prose: String?,
            val leaf: Double,
            val water: Int,
            val seconds: Int,
            val source: RecipeSource,
        )
        listOf(
            Case(null, 10.0, 1000, 4 * 3600, RecipeSource.STYLE_DEFAULT),
            Case(
                "Als Kaltaufguss 4-6 Stunden im Kühlschrank ziehen lassen.",
                10.0,
                1000,
                5 * 3600,
                RecipeSource.STYLE_DEFAULT,
            ),
            Case(
                "Kaltaufguss: 10 g auf 1 Liter kaltes Wasser, 8 Stunden ziehen lassen.",
                10.0,
                1000,
                8 * 3600,
                RecipeSource.CATALOG_TEXT,
            ),
            Case(
                "Kaltaufguss: 30 Minuten ziehen lassen, Dosierung verdoppeln.",
                8.0,
                200,
                30 * 60,
                RecipeSource.CATALOG_TEXT,
            ),
            // A paragraph's line breaks keep it one paragraph: the hours belong to the cold brew.
            Case(
                "Für einen Kaltaufguss 12 g auf 1 Liter geben.\nZiehzeit: 6 Stunden.",
                12.0,
                1000,
                6 * 3600,
                RecipeSource.CATALOG_TEXT,
            ),
        ).forEach { case ->
            val cold = derive(grid, prose = case.prose, coldBrewBadge = case.prose == null).recipes
                .single { it.method == BrewMethod.COLD_BREW }
            val check = assertWithMessage(case.prose.toString())
            check.that(cold.name).isEqualTo("Kaltaufguss")
            check.that(cold.baseTempC).isEqualTo(5)
            check.that(cold.leafGrams).isEqualTo(case.leaf)
            check.that(cold.waterMl).isEqualTo(case.water)
            check.that(cold.steps).containsExactly(RecipeStep(StepKind.INFUSION, case.seconds))
            check.that(cold.isDefault).isFalse()
        }
    }

    @Test
    fun aColdBrewBagsDefaultIsTheColdBrew() {
        val result = derive(path = "/tee/gruener-tee/japan/mizudashi-kaltaufgussbeutel/", name = "Kühle Brise Sencha")

        assertThat(result.default.method).isEqualTo(BrewMethod.COLD_BREW)
        assertThat(result.recipes.first()).isEqualTo(result.default)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
    }

    // --- Step 7 and flags --------------------------------------------------------------------

    @Test
    fun implausibleValuesFallBackToTheStyleAndFlag() {
        listOf(
            kyusu("~4g/200ml", "30°C", "60 Sek", "10 Sek", "10 Sek") to { r: RecipeTemplate -> r.baseTempC == 75 },
            kyusu("~4g/200ml", "~60°C", "15 Min", "10 Sek", "10 Sek") to
                { r: RecipeTemplate -> r.steps[0].durationSeconds == 60 },
            kyusu("~4g/200ml", "~60°C", "2 Sek", "10 Sek", "10 Sek") to
                { r: RecipeTemplate -> r.steps[0].durationSeconds == 60 },
            kyusu("~40g/200ml", "~60°C", "60 Sek", "10 Sek", "10 Sek") to
                { r: RecipeTemplate -> r.leafGrams == 5.0 && r.waterMl == 150 },
            kyusu("~0,5g/200ml", "~60°C", "60 Sek", "10 Sek", "10 Sek") to { r: RecipeTemplate -> r.leafGrams == 5.0 },
        ).forEach { (row, fellBack) ->
            val result = derive(row)
            val check = assertWithMessage(row.cells.joinToString { it.text })
            check.that(fellBack(result.default)).isTrue()
            check.that(result.checkReason).isEqualTo(RecipeCheckReason.IMPLAUSIBLE_VALUE)
        }
    }

    @Test
    fun gridAndProseThatDifferAreAConflictAndTheGridWins() {
        val grid = kyusu("~4g/100ml", "~60°C", "60 Sek", "10 Sek", "10 Sek")
        listOf(
            "Zubereitung bei 80 °C." to true,
            "Zubereitung bei 65 °C." to false,
            "Zubereitung: 90 Sekunden ziehen lassen." to true,
            "Zubereitung: 70 Sekunden ziehen lassen." to false,
            "Zubereitung: 5 g auf 100 ml." to true,
            "Zubereitung: 4,5 g auf 100 ml." to false,
        ).forEach { (prose, conflict) ->
            val result = derive(grid, prose = prose)
            val check = assertWithMessage(prose)
            check.that(result.default.baseTempC).isEqualTo(60)
            check.that(result.default.steps[0].durationSeconds).isEqualTo(60)
            check.that(result.default.leafGrams).isEqualTo(4.0)
            check.that(result.default.source).isEqualTo(RecipeSource.CATALOG_TABLE)
            check.that(result.checkReason).isEqualTo(if (conflict) RecipeCheckReason.TABLE_TEXT_CONFLICT else null)
        }
        // Prose that matches another row of the grid contradicts nothing.
        val twoRows = derive(western("~10g/L", "~80°C", "~2 Min"), grid, prose = "Zubereitung: 2 Minuten bei 80 °C.")
        assertThat(twoRows.checkReason).isNull()
    }

    @Test
    fun anUnmappedStyleIsFlaggedEvenWithAFullGrid() {
        val result = derive(
            western("~10g/L", "~80°C", "~2 Min", "~3 Min"),
            path = "/tee/gruener-tee/china/",
            name = "Wolkenspitze",
        )

        assertThat(result.default.source).isEqualTo(RecipeSource.CATALOG_TABLE)
        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.UNMAPPED_STYLE)
    }

    @Test
    fun onlyTheDefaultRecipeFlagsTheTea() {
        // The western row lacks its temperature (style default), the Kyusu default is complete.
        val result = derive(
            western("~10g/L", "-", "~1 Min"),
            kyusu("~4g/200ml", "~60°C", "60 Sek", "10 Sek", "10 Sek"),
            coldBrewBadge = true,
        )

        assertThat(
            result.recipes.single {
                it.method == BrewMethod.WESTERN
            }.source,
        ).isEqualTo(RecipeSource.STYLE_DEFAULT)
        assertThat(result.checkReason).isNull()
    }

    @Test
    fun theStrongestReasonWins() {
        // From the style and implausible: implausible is named.
        val result = derive(kyusu("~4g/200ml", "30°C", "60 Sek", "-", "-"))

        assertThat(result.checkReason).isEqualTo(RecipeCheckReason.IMPLAUSIBLE_VALUE)
    }

    // --- Valid for the timer ----------------------------------------------------------------

    @Test
    fun everyDerivedRecipeIsValidForTheTimer() {
        // Derive every case of this class once more, then check them all.
        listOf(
            ::example1GyokuroKyusuFromTheGridColdBrewFromTheStyle,
            ::example2SenchaKyusuDefaultWesternWithASecondInfusionColdBrewFromTheStyle,
            ::example3MatchaUsuchaWithTheStylesWhiskTime,
            ::example4FlavouredSenchaColdBrewFromTheProseWithDoubleDose,
            ::temperaturesTakeTheMidpointAndSnapTo5DegreesHalfUp,
            ::timesTakeTheMidpointAndSnapTo5Seconds,
            ::amountsKeepTheStatedVolumeAndScalePerLitreAmounts,
            ::withoutGridTheProseAndTheStyleMakeTheRecipes,
            ::aWesternRecipeWithOneInfusionGetsAQuarterAt5DegreesMoreUnflagged,
            ::aColdBrewComesFromTheBadgeOrTheProse,
            ::aColdBrewBagsDefaultIsTheColdBrew,
            ::implausibleValuesFallBackToTheStyleAndFlag,
            ::gridAndProseThatDifferAreAConflictAndTheGridWins,
        ).forEach { it() }
        assertThat(derived.size).isGreaterThan(40)

        for (result in derived) {
            assertThat(result.recipes.count { it.isDefault }).isEqualTo(1)
            for (recipe in result.recipes) {
                val check = assertWithMessage(recipe.toString())
                check.that(recipe.baseTempC % 5).isEqualTo(0)
                check.that(recipe.baseTempC).isIn(5..100)
                check.that(recipe.leafGrams).isIn(Range.closed(0.1, 100.0))
                check.that(Math.round(recipe.leafGrams * 10) / 10.0).isEqualTo(recipe.leafGrams)
                check.that(recipe.waterMl % 5).isEqualTo(0)
                check.that(recipe.waterMl).isIn(10..3000)
                check.that(recipe.steps.size).isIn(1..30)
                check.that(recipe.steps.any { it.kind != StepKind.RINSE }).isTrue()
                recipe.steps.forEach { step ->
                    // The engine's own check (TimerSession.PlannedStep) …
                    PlannedStep(step.kind, step.durationSeconds, step.tempC)
                    // … and the steps editor's grid (PRF-04): 5 s below 10 min, whole minutes above, ≤ 24 h.
                    check.that(step.durationSeconds).isIn(5..86_400)
                    check.that(step.durationSeconds % if (step.durationSeconds < 600) 5 else 60).isEqualTo(0)
                    step.tempC?.let {
                        check.that(it % 5).isEqualTo(0)
                        check.that(it).isIn(5..100)
                    }
                }
            }
        }
    }
}
