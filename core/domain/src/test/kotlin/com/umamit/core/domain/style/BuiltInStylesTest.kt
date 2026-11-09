package com.umamit.core.domain.style

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.UmamiLevel
import org.junit.Test

/** The built-in styles of 08 §3 (STY-01), as code for the first time (D-076). */
class BuiltInStylesTest {
    @Test
    fun everyStyleOf08Section3Exists() {
        assertThat(BuiltInStyles.all.map { it.key }).containsExactly(
            "gyokuro", "kabusecha", "sencha", "fukamushi_sencha", "shincha", "matcha", "karigane", "bancha",
            "hojicha", "hojicha_powder", "genmaicha", "kamairicha", "tamaryokucha", "longjing", "anji_bai_cha",
            "bi_luo_chun", "korean_green", "flavored_green", "green_generic", "herbal",
            "flavored_white", "white_generic", "flavored_oolong", "oolong_generic", "darjeeling", "assam", "ceylon",
            "flavored_black", "black_generic", "sheng", "shou", "dark_generic", "other",
        ).inOrder()
        assertThat(BuiltInStyles.byKey("nope")).isNull()
    }

    @Test
    fun theFlavouredWhiteAndOolongRowsBrewTheShopsWesternRowInTheMug() {
        // 08 §3 (v0.5.0, #205): the live check's western rows (~10 g/L at 80 °C, ~8 g/L at 95 °C), one infusion.
        val white = BuiltInStyles.byKey("flavored_white")!!
        val oolong = BuiltInStyles.byKey("flavored_oolong")!!
        assertThat(white.category).isEqualTo(TeaCategory.WHITE)
        assertThat(oolong.category).isEqualTo(TeaCategory.OOLONG)
        for (style in listOf(white, oolong)) {
            assertThat(style.umami).isEqualTo(UmamiLevel.LOW)
            assertThat(style.freshnessDays).isEqualTo(120)
            assertThat(style.coldBrew).isNull()
        }
        assertThat(white.recipes.single().let { Triple(it.leafGrams, it.baseTempC, it.steps) })
            .isEqualTo(Triple(2.5, 80, listOf(RecipeStep(StepKind.INFUSION, 120))))
        assertThat(oolong.recipes.single().let { Triple(it.leafGrams, it.baseTempC, it.steps) })
            .isEqualTo(Triple(2.0, 95, listOf(RecipeStep(StepKind.INFUSION, 180))))
    }

    @Test
    fun theShengAndShouRowsBrewGongfuInTheGaiwanFirst() {
        // 08 §3 (v0.5.0, #205): the shop's gaiwan rows (5 / 4 g per 120 ml, boiling, ~10 s), then the mug.
        for ((key, leaf) in listOf("sheng" to 5.0, "shou" to 4.0)) {
            val style = BuiltInStyles.byKey(key)!!
            val check = assertWithMessage(key)
            check.that(style.category).isEqualTo(TeaCategory.DARK)
            check.that(style.umami).isEqualTo(UmamiLevel.NONE)
            check.that(style.freshnessDays).isEqualTo(365)
            check.that(style.defaultRecipe).isEqualTo(
                RecipeTemplate(
                    name = "Gongfu",
                    method = BrewMethod.GONGFU,
                    leafGrams = leaf,
                    waterMl = 120,
                    baseTempC = 100,
                    steps = listOf(10, 10, 10, 15, 20, 30).map { RecipeStep(StepKind.INFUSION, it) },
                    source = RecipeSource.STYLE_DEFAULT,
                    isDefault = true,
                ),
            )
            check.that(style.recipes.map { it.method }).containsExactly(BrewMethod.GONGFU, BrewMethod.WESTERN).inOrder()
        }
    }

    @Test
    fun theHerbalStyleBrewsOneSixMinuteInfusionInTheMugAtBoiling() {
        // 08 §3, D-093 Q5: 2 g / 250 ml in the Becher, 100 °C, one infusion of 6:00, no cold brew.
        val herbal = BuiltInStyles.byKey("herbal")!!
        assertThat(herbal.category).isEqualTo(TeaCategory.HERBAL)
        assertThat(herbal.umami).isEqualTo(UmamiLevel.NONE)
        assertThat(herbal.freshnessDays).isEqualTo(180)
        assertThat(herbal.gramsPerTeaspoon).isEqualTo(1.5)
        assertThat(herbal.japanese).isFalse()
        assertThat(herbal.recipes).containsExactly(
            RecipeTemplate(
                name = "Westlich",
                method = BrewMethod.WESTERN,
                leafGrams = 2.0,
                waterMl = 250,
                baseTempC = 100,
                steps = listOf(RecipeStep(StepKind.INFUSION, 360)),
                source = RecipeSource.STYLE_DEFAULT,
                isDefault = true,
            ),
        )
        assertThat(herbal.coldBrew).isNull()
    }

    @Test
    fun theGenericRowsOfTheOtherCategoriesBrewOneThreeMinuteWesternInfusion() {
        // Q-20, D-100, 08 §3: WESTERN 3 g / 250 ml, one infusion of 3:00 at 85 / 95 / 95 / 100 °C.
        data class Expected(val key: String, val category: TeaCategory, val umami: UmamiLevel, val tempC: Int)
        val expected = listOf(
            Expected("white_generic", TeaCategory.WHITE, UmamiLevel.LOW, 85),
            Expected("oolong_generic", TeaCategory.OOLONG, UmamiLevel.LOW, 95),
            Expected("black_generic", TeaCategory.BLACK, UmamiLevel.NONE, 95),
            Expected("dark_generic", TeaCategory.DARK, UmamiLevel.NONE, 100),
        )
        for (row in expected) {
            val style = BuiltInStyles.byKey(row.key)!!
            val check = assertWithMessage(row.key)
            check.that(style.category).isEqualTo(row.category)
            check.that(style.umami).isEqualTo(row.umami)
            check.that(style.freshnessDays).isEqualTo(90)
            check.that(style.japanese).isFalse()
            check.that(style.coldBrew).isNull()
            check.that(style.recipes).containsExactly(
                RecipeTemplate(
                    name = "Westlich",
                    method = BrewMethod.WESTERN,
                    leafGrams = 3.0,
                    waterMl = 250,
                    baseTempC = row.tempC,
                    steps = listOf(RecipeStep(StepKind.INFUSION, 180)),
                    source = RecipeSource.STYLE_DEFAULT,
                    isDefault = true,
                ),
            )
        }
    }

    @Test
    fun theBlackRowsOfTheCatalogBrewOneWesternInfusionInTheMug() {
        // 08 §3 (v0.5.0, D-108): 2,5 g / 250 ml (the shop's ~10 g/L), one infusion; no umami, no cold brew.
        data class Expected(val key: String, val fresh: Int, val teaspoon: Double, val tempC: Int, val seconds: Int)
        val expected = listOf(
            Expected("darjeeling", 120, 2.0, 95, 150),
            Expected("assam", 180, 3.0, 100, 180),
            Expected("ceylon", 180, 2.5, 100, 180),
            Expected("flavored_black", 120, 2.5, 100, 180),
        )
        for (row in expected) {
            val style = BuiltInStyles.byKey(row.key)!!
            val check = assertWithMessage(row.key)
            check.that(style.category).isEqualTo(TeaCategory.BLACK)
            check.that(style.umami).isEqualTo(UmamiLevel.NONE)
            check.that(style.freshnessDays).isEqualTo(row.fresh)
            check.that(style.gramsPerTeaspoon).isEqualTo(row.teaspoon)
            check.that(style.japanese).isFalse()
            check.that(style.coldBrew).isNull()
            check.that(style.recipes).containsExactly(
                RecipeTemplate(
                    name = "Westlich",
                    method = BrewMethod.WESTERN,
                    leafGrams = 2.5,
                    waterMl = 250,
                    baseTempC = row.tempC,
                    steps = listOf(RecipeStep(StepKind.INFUSION, row.seconds)),
                    source = RecipeSource.STYLE_DEFAULT,
                    isDefault = true,
                ),
            )
        }
    }

    @Test
    fun otherHasNoUmamiAndNoFreshnessWindow() {
        // 08 §3: „Andere" — umami and freshness „—", 2,5 g/TL, WESTERN 3 g / 250 ml / 85 °C: 3:00.
        val other = BuiltInStyles.byKey("other")!!
        assertThat(other.category).isEqualTo(TeaCategory.OTHER)
        assertThat(other.umami).isNull()
        assertThat(other.freshnessDays).isNull()
        assertThat(other.gramsPerTeaspoon).isEqualTo(2.5)
        assertThat(other.recipes).containsExactly(
            RecipeTemplate(
                name = "Westlich",
                method = BrewMethod.WESTERN,
                leafGrams = 3.0,
                waterMl = 250,
                baseTempC = 85,
                steps = listOf(RecipeStep(StepKind.INFUSION, 180)),
                source = RecipeSource.STYLE_DEFAULT,
                isDefault = true,
            ),
        )
    }

    @Test
    fun everyStyleBelongsToItsKategorie() {
        assertThat(BuiltInStyles.all.groupBy({ it.category }, { it.key }).mapValues { it.value.size })
            .containsExactly(
                TeaCategory.GREEN, 19,
                TeaCategory.HERBAL, 1,
                TeaCategory.WHITE, 2,
                TeaCategory.OOLONG, 2,
                TeaCategory.BLACK, 5,
                TeaCategory.DARK, 3,
                TeaCategory.OTHER, 1,
            )
    }

    @Test
    fun valuesAsIn08Section3() {
        val sencha = BuiltInStyles.byKey("sencha")!!
        assertThat(sencha.umami).isEqualTo(UmamiLevel.MEDIUM)
        assertThat(sencha.freshnessDays).isEqualTo(45)
        assertThat(sencha.gramsPerTeaspoon).isEqualTo(2.5)
        assertThat(sencha.defaultRecipe).isEqualTo(
            RecipeTemplate(
                name = "Kyusu",
                method = BrewMethod.KYUSU,
                leafGrams = 5.0,
                waterMl = 150,
                baseTempC = 75,
                steps = listOf(
                    RecipeStep(StepKind.INFUSION, 60),
                    RecipeStep(StepKind.INFUSION, 15, tempC = 80),
                    RecipeStep(StepKind.INFUSION, 45, tempC = 85),
                ),
                source = RecipeSource.STYLE_DEFAULT,
                isDefault = true,
            ),
        )
        assertThat(sencha.coldBrew).isEqualTo(
            RecipeTemplate(
                name = "Kaltaufguss",
                method = BrewMethod.COLD_BREW,
                leafGrams = 10.0,
                waterMl = 1000,
                baseTempC = 5,
                steps = listOf(RecipeStep(StepKind.INFUSION, 4 * 3600)),
            ),
        )

        val matcha = BuiltInStyles.byKey("matcha")!!
        assertThat(matcha.defaultRecipe.method).isEqualTo(BrewMethod.MATCHA_USUCHA)
        assertThat(matcha.defaultRecipe.steps).containsExactly(RecipeStep(StepKind.WHISK, 20))

        val gyokuro = BuiltInStyles.byKey("gyokuro")!!
        assertThat(gyokuro.umami).isEqualTo(UmamiLevel.VERY_HIGH)
        assertThat(gyokuro.coldBrew?.leafGrams).isEqualTo(10.0)
        assertThat(gyokuro.coldBrew?.waterMl).isEqualTo(500)
        assertThat(gyokuro.recipes.map { it.method })
            .containsExactly(BrewMethod.KYUSU, BrewMethod.COLD_BREW, BrewMethod.ICE_BREW).inOrder()
        assertThat(gyokuro.hotRecipes.map { it.method }).containsExactly(BrewMethod.KYUSU)

        val generic = BuiltInStyles.GENERIC
        assertThat(generic.key).isEqualTo("green_generic")
        assertThat(
            generic.hotRecipes.map {
                it.method
            },
        ).containsExactly(BrewMethod.WESTERN, BrewMethod.GONGFU).inOrder()
        assertThat(generic.japanese).isFalse()
    }

    @Test
    fun aHojichaPowderIsWhiskedLikeAMatchaNeverSteeped() {
        // Full live run 1 (D-091): a powder gets no leaf recipe and no cold brew (08 §3).
        val powder = BuiltInStyles.byKey("hojicha_powder")!!
        assertThat(powder.umami).isEqualTo(UmamiLevel.LOW)
        assertThat(powder.japanese).isTrue()
        assertThat(powder.recipes).containsExactly(
            RecipeTemplate(
                name = "Usucha",
                method = BrewMethod.MATCHA_USUCHA,
                leafGrams = 2.0,
                waterMl = 70,
                baseTempC = 80,
                steps = listOf(RecipeStep(StepKind.WHISK, 20)),
                source = RecipeSource.STYLE_DEFAULT,
                isDefault = true,
            ),
        )
        assertThat(powder.coldBrew).isNull()
    }

    @Test
    fun onlyGyokuroAndSenchaHaveAColdBrewOthersUseSenchas() {
        assertThat(BuiltInStyles.all.filter { it.coldBrew != null }.map { it.key }).containsExactly("gyokuro", "sencha")
        // D-076: styles without a cold-brew default use sencha's.
        assertThat(BuiltInStyles.byKey("flavored_green")!!.coldBrewOrFallback)
            .isEqualTo(BuiltInStyles.byKey("sencha")!!.coldBrew)
        assertThat(BuiltInStyles.byKey("gyokuro")!!.coldBrewOrFallback.waterMl).isEqualTo(500)
    }

    @Test
    fun everyRecipeIsValidForTheTimer() {
        for (style in BuiltInStyles.all) {
            assertThat(style.recipes.count { it.isDefault }).isEqualTo(1)
            assertThat(style.defaultRecipe).isEqualTo(style.hotRecipes.first())
            for (recipe in style.recipes) {
                val check = assertWithMessage("${style.key} ${recipe.name}")
                check.that(recipe.baseTempC % 5).isEqualTo(0)
                check.that(recipe.baseTempC).isIn(5..100)
                check.that(recipe.leafGrams).isAtLeast(0.1)
                check.that(recipe.waterMl % 5).isEqualTo(0)
                check.that(recipe.steps).isNotEmpty()
                recipe.steps.forEach { step ->
                    check.that(step.durationSeconds).isIn(5..86_400)
                    step.tempC?.let { check.that(it % 5).isEqualTo(0) }
                }
            }
        }
    }
}
