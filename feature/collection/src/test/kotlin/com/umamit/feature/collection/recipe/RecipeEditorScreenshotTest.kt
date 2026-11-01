package com.umamit.feature.collection.recipe

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Recipe
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.model.Vessel
import com.umamit.core.model.VesselType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Invented recipes of SCR-12 (D-016). */
internal object RecipeEditorStates {
    private val kyusu250 = Vessel("k250", "Kyusu 250 ml", VesselType.KYUSU, capacityMl = 300, usableMl = 250)
    private val mug = Vessel("becher", "Becher mit Sieb", VesselType.MUG_INFUSER, capacityMl = 400, isFavorite = true)
    private val oldPot = Vessel("alt", "Alte Kanne", VesselType.WESTERN_TEAPOT, capacityMl = 800, isArchived = true)

    private val recipe = Recipe(
        id = "kyusu",
        teaId = "tea",
        name = "Kyusu, morgens",
        method = BrewMethod.KYUSU,
        leafGrams = 5.0,
        waterMl = 250,
        baseTempC = 60,
        steps = listOf(
            RecipeStep(StepKind.RINSE, 10),
            RecipeStep(StepKind.INFUSION, 90, preAlertSeconds = 20),
            RecipeStep(StepKind.INFUSION, 30, tempC = 80, note = "Etwas heißer, kurz."),
        ),
        vesselId = "k250",
        isDefault = true,
        notes = "Weiches Wasser, Kanne vorwärmen.",
    )

    /** A stored default recipe with three steps. */
    val edit = RecipeEditorUiState.Ready(
        form = RecipeForm.of(recipe),
        isNew = false,
        isStoredDefault = true,
        canDelete = true,
        vessels = listOf(mug, kyusu250),
        selectedVessel = kyusu250,
        globalPreAlertSeconds = 10,
        hasChanges = false,
        errors = emptySet(),
    )

    /** *Rezept hinzufügen* after a refused *Speichern*, with the default's archived vessel. */
    val newWithErrors = RecipeEditorUiState.Ready(
        form = RecipeForm.of(recipe.copy(vesselId = "alt", isDefault = false)).copy(name = "", water = "247"),
        isNew = true,
        isStoredDefault = false,
        canDelete = false,
        vessels = listOf(mug, kyusu250),
        selectedVessel = oldPot,
        globalPreAlertSeconds = 10,
        hasChanges = true,
        errors = setOf(RecipeFieldError.NameEmpty, RecipeFieldError.Water),
    )
}

/**
 * SCR-12 in light and dark at font scale 1.0 and 2.0 (03 §6): a stored recipe (top, then scrolled to its steps) and a
 * new one after a refused *Speichern*. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class RecipeEditorScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, scrollToTag: String? = null, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { UmamiTheme(darkTheme = dark, reduceMotion = true) { content() } }
        compose.waitForIdle()
        scrollToTag?.let { compose.onNodeWithTag(it).performScrollTo() }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_$variant.png")
    }

    @Test
    fun edit() = capture("RecipeEditor_edit") { RecipeEditorScreen(RecipeEditorStates.edit, RecipeEditorActions()) }

    @Test
    fun steps() = capture("RecipeEditor_steps", scrollToTag = RecipeStepTags.step(3)) {
        RecipeEditorScreen(RecipeEditorStates.edit, RecipeEditorActions())
    }

    @Test
    fun newWithErrors() = capture("RecipeEditor_new") {
        RecipeEditorScreen(RecipeEditorStates.newWithErrors, RecipeEditorActions())
    }
}

/** SCR-12 on a short screen (360 × 640 dp) at font scale 2.0: the top bar keeps ✕, *Speichern* and ⋮. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
class RecipeEditorShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editor() {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                RecipeEditorScreen(RecipeEditorStates.edit, RecipeEditorActions())
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeStepTags.step(2)).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/RecipeEditor_steps_short_font2_0.png")
    }
}
