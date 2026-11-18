package com.umamit.feature.collection.receive

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.backup.recipe.RecipeRefusal
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TeaCategory
import com.umamit.feature.collection.receive.ReceivedRecipes.CATALOG_KEY
import com.umamit.feature.collection.receive.ReceivedRecipes.kyusu
import com.umamit.feature.collection.receive.ReceivedRecipes.plain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-32 „Rezept übernehmen" (PRF-08, D-130) in light and dark at font scale 1.0 and 2.0 (03 §6): a tea found by the
 * catalog key, no match with *Neuen Tee anlegen* „aus dem Katalog", a tea not in the local catalog with *Tee über
 * Shop-Link laden*, and a refused file. Synthetic teas with the leaf placeholder (D-016). Goldens:
 * `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ReceiveRecipeScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val gyokuro = TeaOption(
            id = "t1",
            name = "Gyokuro Asahi",
            category = TeaCategory.GREEN,
            sorte = "Gyokuro",
            secondaryName = "Testtal",
            recipeCount = 2,
            catalogKey = CATALOG_KEY,
        )
        private val full = TeaOption(id = "t2", name = "Morgennebel", category = TeaCategory.GREEN, recipeCount = 10)
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, state: ReceiveRecipeUiState) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                ReceiveRecipeScreen(state, ReceiveRecipeActions(), photo = { null })
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/ReceiveRecipe_${name}_$variant.png")
    }

    @Test
    fun match() = capture(
        "match",
        ReceiveRecipeUiState.Ready(
            recipe = kyusu,
            match = gyokuro,
            matchKind = MatchKind.CATALOG,
            choice = TeaChoice.Existing("t1"),
            teas = listOf(gyokuro, full),
            catalogKey = CATALOG_KEY,
        ),
    )

    @Test
    fun noMatch() = capture(
        "noMatch",
        ReceiveRecipeUiState.Ready(recipe = kyusu, teas = listOf(full), catalogKey = CATALOG_KEY),
    )

    @Test
    fun notInCatalog() = capture(
        "notInCatalog",
        ReceiveRecipeUiState.Ready(recipe = kyusu, teas = listOf(full), offersShopLink = true),
    )

    @Test
    fun withoutCatalog() = capture(
        "withoutCatalog",
        ReceiveRecipeUiState.Ready(recipe = plain, choice = TeaChoice.NewTea),
    )

    @Test
    fun refused() = capture("refused", ReceiveRecipeUiState.Refused(RecipeRefusal.NEWER_VERSION))
}
