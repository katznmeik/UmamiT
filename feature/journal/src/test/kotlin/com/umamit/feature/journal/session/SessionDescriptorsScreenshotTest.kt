package com.umamit.feature.journal.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.TasteDescriptor
import com.umamit.core.model.TastingLimits
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * BESCHREIBUNGEN on the session page and the descriptor sheet (SCR-16, SCR-30, JRN-04, D-112) in light and dark at
 * font scale 1.0 and 2.0 (03 §6): the section empty and with chips, the sheet open, searched and at 20. The sheet is
 * composed open, as the filter sheet's goldens do. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SessionDescriptorsScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val CHOSEN = listOf(
            TasteDescriptor.MARINE_NORI,
            TasteDescriptor.SWEET_ZUCKERMAIS,
            TasteDescriptor.MOUTHFEEL_CREMIG,
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun file(name: String) =
        "${roborazziSystemPropertyOutputDirectory()}/SessionDescriptors_${name}_$variant.png"

    private fun section(name: String, keys: List<TasteDescriptor>) {
        RuntimeEnvironment.setFontScale(fontScale)
        val page = SessionPageScreenshotTest.page.copy(descriptorKeys = keys.map { it.key })
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) { SessionPageScreen(SessionPageUiState.Ready(page)) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(SESSION_DESCRIPTORS_ADD_TAG).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage(file(name))
    }

    private fun sheet(name: String, chosen: List<TasteDescriptor>, search: String? = null) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                DescriptorSheet(
                    chosen = chosen.map { it.key }.toSet(),
                    total = chosen.size,
                    onToggle = { _, _ -> },
                    onDismissRequest = {},
                )
            }
        }
        if (search != null) compose.onNodeWithTag(DESCRIPTOR_SEARCH_TAG).performTextInput(search)
        compose.waitForIdle()
        captureScreenRoboImage(file(name))
    }

    @Test
    fun sectionEmpty() = section("sectionEmpty", emptyList())

    @Test
    fun sectionWithChips() = section("sectionWithChips", CHOSEN)

    @Test
    fun sheetOpen() = sheet("sheetOpen", CHOSEN)

    @Test
    fun sheetSearched() = sheet("sheetSearched", CHOSEN, search = "nor")

    @Test
    fun sheetAtTwenty() = sheet("sheetAtTwenty", TasteDescriptor.entries.take(TastingLimits.MAX_DESCRIPTORS))
}
