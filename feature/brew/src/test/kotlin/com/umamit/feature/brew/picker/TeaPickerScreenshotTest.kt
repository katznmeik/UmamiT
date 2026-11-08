package com.umamit.feature.brew.picker

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.Freshness
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tea picker (SCR-03) with the small freshness chips (INV-06, D-104) in light and dark at font scale 1.0 and
 * 2.0 (03 §6), and on a short screen (360 × 640 dp) at 2.0. Invented teas, the placeholder leaf (D-016).
 * Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TeaPickerScreenshotTest(private val dark: Boolean, private val fontScale: Float, private val short: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}, short={2}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale, false) }
        } + listOf(arrayOf<Any>(false, 2f, true))

        private val teas = listOf(
            PickerTea("m", "Mondschatten", "Gyokuro", null, freshness = Freshness.DRINK_SOON),
            PickerTea("f", "Morgentau", "Sencha", null, freshness = Freshness.FRESH),
            PickerTea("h", "Herbstwind", "Hojicha", null, freshness = Freshness.PAST_PEAK),
            PickerTea("v", "Versiegelt", "Kabusecha", null),
        )

        val state = TeaPickerUiState(recent = teas.take(1), all = teas.sortedBy { it.name }, loading = false)
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val variant: String
        get() = if (short) {
            "short_font2_0"
        } else {
            "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"
        }

    @Test
    fun freshness() {
        RuntimeEnvironment.setQualifiers(if (short) "w360dp-h640dp-xhdpi" else "w411dp-h891dp-xhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                TeaPickerSheet(
                    state = state,
                    onQueryChange = {},
                    onPickTea = {},
                    onQuickTimer = {},
                    onDismissRequest = {},
                    onCatalog = {},
                )
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/TeaPicker_freshness_$variant.png")
    }
}
