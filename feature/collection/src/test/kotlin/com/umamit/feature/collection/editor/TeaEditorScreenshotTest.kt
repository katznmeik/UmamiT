package com.umamit.feature.collection.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.UmamiLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Invented forms of SCR-10 (D-016). */
internal object TeaEditorStates {
    private val today: LocalDate = LocalDate.of(2026, 10, 22)

    /** A new Sencha after *Speichern* without a name and with a year in the future. */
    val newWithErrors = TeaEditorUiState.Ready(
        form = TeaEditorForm.new("sencha", today).copy(harvestYear = "2027", packageGrams = "50", price = "12,40"),
        isNew = true,
        today = today,
        hasChanges = true,
        errors = setOf(TeaFieldError.NameEmpty, TeaFieldError.HarvestYearFuture),
    )

    /** A stored Gyokuro with its origin. */
    val edit = TeaEditorUiState.Ready(
        form = TeaEditorForm(
            name = "Asahi no Shizuku",
            secondaryName = "Morgentau",
            styleKey = "gyokuro",
            umami = UmamiLevel.VERY_HIGH,
            originCountry = "JP",
            region = "Yame, Fukuoka",
            cultivar = "Saemidori",
            harvestYear = "2026",
            harvestSeason = HarvestSeason.FIRST_FLUSH,
            shadingDays = "21",
            vendor = "Tee-Kontor Kiel",
            notes = "Morgens mit 50 °C.",
        ),
        isNew = false,
        today = today,
        hasChanges = false,
        errors = emptySet(),
    )
}

/**
 * SCR-10 and SCR-11 in light and dark at font scale 1.0 and 2.0 (03 §6): a new tea after a refused *Speichern*
 * (top, then scrolled to *Kauf*), a stored tea, and *Sorte wählen*. Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaEditorScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

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
    fun newWithErrors() = capture("TeaEditor_new") {
        TeaEditorScreen(TeaEditorStates.newWithErrors, TeaEditorActions())
    }

    @Test
    fun newPurchase() = capture("TeaEditor_newPurchase", scrollToTag = TeaEditorTags.PRICE) {
        TeaEditorScreen(TeaEditorStates.newWithErrors, TeaEditorActions())
    }

    @Test
    fun edit() = capture("TeaEditor_edit") { TeaEditorScreen(TeaEditorStates.edit, TeaEditorActions()) }

    @Test
    fun sortePicker() = capture("SortePicker") { SortePickerScreen(onBack = {}, onPick = {}) }
}

/** SCR-10 and SCR-11 on a short screen (360 × 640 dp) at font scale 2.0: the top bar keeps ✕ and *Speichern*. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
class TeaEditorShortScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { UmamiTheme(darkTheme = false, reduceMotion = true) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_short_font2_0.png")
    }

    @Test
    fun editor() = capture("TeaEditor_new") { TeaEditorScreen(TeaEditorStates.newWithErrors, TeaEditorActions()) }

    @Test
    fun sortePicker() = capture("SortePicker") { SortePickerScreen(onBack = {}, onPick = {}) }
}
