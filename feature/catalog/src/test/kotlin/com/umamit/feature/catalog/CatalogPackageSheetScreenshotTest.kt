package com.umamit.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.testing.FakeCatalogRepository
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeExactPrices
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SCR-32's package sheet (*Neuen Tee anlegen* „aus dem Katalog", PRF-08): the catalog's add sheet that only hands the
 * purchase back, open and after a failed write, in light and dark at font scale 1.0 and 2.0 (03 §6), for the design
 * review. Invented tea, no shop photos (D-016). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogPackageSheetScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private const val PRODUCT = "https://shop.example/morgennebel-sencha/XY0100"

        private val SENCHA = CatalogTeaDetail(
            key = "XY0100",
            name = "Morgennebel Sencha",
            productUrl = PRODUCT,
            teaType = "Sencha",
            basePriceCentsPer100g = 1240,
            available = true,
            packages = listOf(
                CatalogPackage("XY0100", "100 g", 100.0, 1240, false, null, PRODUCT, true),
                CatalogPackage("XY0100-250", "250 g · 3 % gespart", 250.0, 3007, false, 3, "$PRODUCT-250", null),
            ),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, failed: Boolean) {
        RuntimeEnvironment.setFontScale(fontScale)
        val viewModel = CatalogPackageSheetViewModel(
            FakeCatalogRepository(details = listOf(SENCHA)),
            FakeExactPrices(),
            FakeClock(),
        )
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    CatalogPackageSheet(
                        catalogKey = SENCHA.key,
                        saving = false,
                        failed = failed,
                        onSave = {},
                        onDismiss = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/CatalogPackageSheet_${name}_$variant.png")
    }

    /** The sheet as *Rezept übernehmen* opens it: the sizes, the price, today's date and *Aufnehmen*. */
    @Test
    fun packageSheet() = capture("open", failed = false)

    /** After a failed write of the tea, its package and the recipe: the sheet stays with its message. */
    @Test
    fun packageSheetFailed() = capture("failed", failed = true)
}
