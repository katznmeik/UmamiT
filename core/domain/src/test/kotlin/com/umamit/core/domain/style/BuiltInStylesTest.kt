package com.umamit.core.domain.style

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.UmamiLevel
import org.junit.Test

/** The built-in styles of 08 §3 (STY-01), as code for the first time (D-076). */
class BuiltInStylesTest {
    @Test
    fun everyGreenStyleOf08Section3Exists() {
        assertThat(BuiltInStyles.all.map { it.key }).containsExactly(
            "gyokuro", "kabusecha", "sencha", "fukamushi_sencha", "shincha", "matcha", "karigane", "bancha",
            "hojicha", "hojicha_powder", "genmaicha", "kamairicha", "tamaryokucha", "longjing", "anji_bai_cha",
            "bi_luo_chun", "korean_green", "flavored_green", "green_generic",
        ).inOrder()
        assertThat(BuiltInStyles.byKey("nope")).isNull()
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
