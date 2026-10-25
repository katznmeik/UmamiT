package com.umamit.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.data.catalog.CatalogRefreshOutcome
import com.umamit.core.data.catalog.CatalogRefreshProgress
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewTableCell
import com.umamit.core.model.BrewTableRow
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteScale
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
 * Sammlung › Katalog (SCR-19), the refresh sheet (SCR-22) and a tea's detail (SCR-21) in light
 * and dark: the empty state, the list with its thumbnails and the detail (top and end) also at
 * font scale 2.0 (03 §6), the sheet's progress, result and error at 1.0 (spec #62 seam 4).
 * Invented teas (D-016); the photos are placeholder gradients, never shop photos. Goldens:
 * `src/test/screenshots/`.
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
            catalogTea("XY0100", "Morgennebel Sencha", thumbnailPath = "sencha"),
            catalogTea("XY0200", "Tautropfen Gyokuro", "Gyokuro", 3600, thumbnailPath = "gyokuro"),
            catalogTea("XY0300", "Frühlingshauch Shincha 2026 aus dem ersten Austrieb", "Shincha", 2190),
            catalogTea("XY0400", "Kirschblüte (aromatisiert)", teaType = null, basePriceCentsPer100g = 790)
                .copy(recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT),
            catalogTea("XY0500", "Wolkenberg Matcha", "Matcha Teepulver", 12_995, thumbnailPath = "matcha"),
            catalogTea("XY0600", "Sonnenhang Bancha", "Bancha", null),
        )

        /** A placeholder image in place of a photo (D-016: no shop photos in the repository). */
        private fun placeholderPhoto(seed: String) = BrushPainter(
            Brush.linearGradient(
                listOf(Color(0xFF7C9A5B), Color(0xFFC9D58F), Color(0xFF4A5643)).let {
                    if (seed.length % 2 == 0) it else it.reversed()
                },
            ),
        )

        private val DETAIL = CatalogTeaDetail(
            key = "XY0100",
            name = "Morgennebel Sencha",
            secondaryName = "朝霧",
            productUrl = "https://shop.example/morgennebel-sencha/XY0100",
            teaType = "Sencha",
            originCountry = "Japan",
            originRegion = "Kirishima",
            cultivar = "Yabukita, Saemidori",
            harvest = "Frühjahr 2026",
            shading = "7 Tage",
            steaming = "Chumushi",
            season = "Frühling, Sommer",
            isOrganic = true,
            coldBrewSuitable = true,
            aroma = "naturbelassen",
            tasteNotes = "grasig, süßlich, nussig",
            tasteScales = listOf(
                TasteScale("Umami", 4),
                TasteScale("Bitterstoffe", 1),
                TasteScale("Süße", 3),
                TasteScale("Frische", 5),
            ),
            ingredients = "Grüner Tee aus kontrolliert ökologischem Anbau",
            descriptionHtml = "<p>Ein erfundener Sencha für die Bildschirmfotos: <b>frisch</b> und mild, " +
                "mit einem langen, süßen Nachklang.</p><p>Er wächst an einem erfundenen Hang im Morgennebel, " +
                "der die Blätter vor der Sonne schützt.</p>",
            brewTable = listOf(
                BrewTableRow(
                    BrewTableRow.KIND_JAPAN,
                    listOf(
                        BrewTableCell("Menge", "~4g/100ml"),
                        BrewTableCell("Temperatur", "~60°C"),
                        BrewTableCell("1. Aufguss", "~90 Sek"),
                        BrewTableCell("2. Aufguss", "~20 Sek"),
                        BrewTableCell("3. Aufguss", "~60 Sek"),
                    ),
                ),
                BrewTableRow(
                    BrewTableRow.KIND_WESTERN,
                    listOf(
                        BrewTableCell("Menge", "~10g/L"),
                        BrewTableCell("Temperatur", "~65°C"),
                        BrewTableCell("1. Aufguss", "~1 Min"),
                        BrewTableCell("2. Aufguss", "-"),
                        BrewTableCell("3. Aufguss", "-"),
                    ),
                ),
            ),
            brewText = "Zubereitung: 4 g auf 100 ml bei 60 °C, der erste Aufguss 90 Sekunden.",
            recipes = listOf(
                RecipeTemplate(
                    "Kyusu",
                    BrewMethod.KYUSU,
                    4.0,
                    100,
                    60,
                    listOf(90, 20, 60).map {
                        RecipeStep(StepKind.INFUSION, it)
                    },
                    RecipeSource.CATALOG_TABLE,
                    isDefault = true,
                ),
                RecipeTemplate(
                    "Westlich",
                    BrewMethod.WESTERN,
                    2.5,
                    250,
                    65,
                    listOf(RecipeStep(StepKind.INFUSION, 60), RecipeStep(StepKind.INFUSION, 15, tempC = 70)),
                    RecipeSource.CATALOG_TABLE,
                ),
                RecipeTemplate(
                    "Kaltaufguss",
                    BrewMethod.COLD_BREW,
                    10.0,
                    1000,
                    5,
                    listOf(RecipeStep(StepKind.INFUSION, 4 * 3600)),
                    RecipeSource.STYLE_DEFAULT,
                ),
            ),
            basePriceCentsPer100g = 1240,
            available = true,
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, scrollToText: String? = null, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.waitForIdle()
        if (scrollToText != null) {
            compose.onNodeWithText(scrollToText).performScrollTo()
            compose.waitForIdle()
        }
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
        CatalogContent(
            state = listState(),
            onLoad = {},
            onShowProgress = {},
            zone = FakeClock.BERLIN,
            thumbnail = { path -> path?.let { remember(it) { placeholderPhoto(it) } } },
        )
    }

    @Test
    fun detail() = capture("detail") { Detail() }

    /** The detail scrolled to its end: description and ZUBEREITUNG. */
    @Test
    fun detailEnd() = capture("detailEnd", scrollToText = "Angaben des Shops, unverändert übernommen.") { Detail() }

    /** ABGELEITETES REZEPT with „Rezept prüfen" and its reason (CAT-04, CAT-05). */
    @Test
    fun detailRecipes() = capture("detailRecipes", scrollToText = "Fehlende Werte aus den Standardwerten der Sorte") {
        Detail(DETAIL.copy(recipeCheckReason = RecipeCheckReason.TABLE_TEXT_CONFLICT))
    }

    @Composable
    private fun Detail(tea: CatalogTeaDetail = DETAIL) {
        CatalogTeaScreen(
            state = CatalogTeaUiState(loading = false, tea = tea, shopUrl = tea.productUrl),
            onBack = {},
            onOpenShop = {},
            photo = remember { placeholderPhoto("detail") },
        )
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
