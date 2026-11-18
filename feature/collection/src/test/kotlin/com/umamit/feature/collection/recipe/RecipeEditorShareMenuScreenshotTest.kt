package com.umamit.feature.collection.recipe

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-12's ⋮ opened with *Rezept teilen* above *Rezept löschen* (PRF-08, D-130) in light and dark: a saved recipe,
 * and one with unsaved changes („Erst speichern"). Invented recipe (D-016). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class RecipeEditorShareMenuScreenshotTest(private val dark: Boolean, private val unsaved: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, unsaved={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(false, true).map { unsaved -> arrayOf<Any>(dark, unsaved) }
        }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun menuOpen() {
        val state = RecipeEditorStates.edit.copy(hasChanges = unsaved)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) { RecipeEditorScreen(state, RecipeEditorActions()) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(RecipeEditorTags.MORE).performClick()
        compose.waitForIdle()
        val name = "RecipeEditor_shareMenu${if (unsaved) "_unsaved" else ""}_${if (dark) "dark" else "light"}"
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/$name.png")
    }
}
