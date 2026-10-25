package com.umamit.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.catalogTea
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sammlung › Katalog (SCR-19) and the refresh sheet (SCR-22) in light and dark: the empty state
 * and the list also at font scale 2.0 (03 §6), the sheet's progress, result and error at 1.0
 * (spec #62 seam 4). Invented teas (D-016). Goldens: `src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CatalogScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private val TEAS = listOf(
            catalogTea("XY0100", "Morgennebel Sencha"),
            catalogTea("XY0200", "Tautropfen Gyokuro", "Gyokuro", 3600),
            catalogTea("XY0300", "Frühlingshauch Shincha 2026 aus dem ersten Austrieb", "Shincha", 2190),
            catalogTea("XY0400", "Kirschblüte (aromatisiert)", teaType = null, basePriceCentsPer100g = 790),
            catalogTea("XY0500", "Wolkenberg Matcha", "Matcha Teepulver", 12_995),
            catalogTea("XY0600", "Sonnenhang Bancha", "Bancha", null),
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${roborazziSystemPropertyOutputDirectory()}/Catalog_${name}_$variant.png")
    }

    private fun listState(refresh: CatalogRefreshState = CatalogRefreshState.Idle) = CatalogUiState(
        teas = TEAS,
        lastRefreshAtMillis = FakeClock.DEFAULT_WALL_MS,
        refresh = refresh,
        loading = false,
    )

    @Test
    fun empty() = capture("empty") {
        CatalogContent(
            state = CatalogUiState(loading = false),
            onLoad = {},
            onShowProgress = {},
            zone = FakeClock.BERLIN,
        )
    }

    @Test
    fun list() = capture("list") {
        CatalogContent(state = listState(), onLoad = {}, onShowProgress = {}, zone = FakeClock.BERLIN)
    }

    @Test
    fun refreshRunning() = sheet("refreshRunning", CatalogRefreshState.Running(CatalogRefreshProgress(412, 1560, 96)))

    @Test
    fun refreshSuccess() =
        sheet("refreshSuccess", CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(274, 274)))

    @Test
    fun refreshShopChanged() =
        sheet("refreshShopChanged", CatalogRefreshState.Finished(CatalogRefreshOutcome.ShopChanged("x")))

    /** The sheet over the empty catalog, at font scale 1.0 only (spec #62 seam 4). */
    private fun sheet(name: String, refresh: CatalogRefreshState) {
        assumeTrue(fontScale == 1f)
        capture(name) {
            CatalogContent(state = CatalogUiState(refresh = refresh, loading = false), onLoad = {}, onShowProgress = {})
            CatalogRefreshSheet(refresh = refresh, onCancel = {}, onDismissRequest = {})
        }
    }
}
