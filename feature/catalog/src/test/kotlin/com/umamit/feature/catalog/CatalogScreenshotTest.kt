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
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.catalog.CatalogBrowser
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewTableCell
import com.umamit.core.model.BrewTableRow
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogSort
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.RecipeSource
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.RecipeTemplate
import com.umamit.core.model.StepKind
import com.umamit.core.model.TasteScale
import com.umamit.core.model.UmamiLevel
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
import java.time.LocalDate

/**
 * Sammlung › Katalog (SCR-19), the refresh sheet (SCR-22) and a tea's detail (SCR-21) in light
 * and dark: the empty state, the list with its thumbnails and the detail (top, end and packages) also at
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

        private const val ESTIMATE_NOTE =
            "≈ geschätzt aus dem Preis im Shop. Den genauen Preis holt die App, wenn du die Packung hinzufügst."

        private val PURCHASE_DAY: LocalDate = LocalDate.of(2026, 10, 22)

        private val TEAS = listOf(
            catalogTea("XY0100", "Morgennebel Sencha", thumbnailPath = "sencha", secondaryName = "朝霧")
                .copy(originCountry = "Japan", isOrganic = true, owned = true),
            catalogTea("XY0200", "Tautropfen Gyokuro", "Gyokuro", 3600, thumbnailPath = "gyokuro")
                .copy(secondaryName = "Tamatsuyu", originCountry = "Japan", isNew = true, marked = true),
            catalogTea("XY0300", "Frühlingshauch Shincha 2026 aus dem ersten Austrieb", "Shincha", 2190).copy(
                // The worst case: a secondary name longer than one line is cut off (CAT-10, D-090).
                secondaryName = "Haru no ibuki ichiban-cha aus dem Hochland von Kagoshima",
                originCountry = "Japan",
                recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT,
            ),
            catalogTea("XY0400", "Kirschblüte", teaType = null, basePriceCentsPer100g = 790)
                .copy(originCountry = "China", isFlavored = true),
            catalogTea("XY0500", "Wolkenberg Matcha", "Matcha Teepulver", 12_995, thumbnailPath = "matcha")
                .copy(originCountry = "Japan", isOrganic = true, isNew = true),
            catalogTea("XY0600", "Sonnenhang Bancha", "Bancha", null).copy(originCountry = "Japan, Korea"),
            catalogTea("XY0700", "Bergquelle Sencha", basePriceCentsPer100g = 990).copy(originCountry = "China"),
            catalogTea("XY0800", "Kirsch Sencha", basePriceCentsPer100g = 1090).copy(isFlavored = true),
            catalogTea("XY0900", "Stiller See Gyokuro", "Gyokuro", 4400).copy(available = false, marked = true),
            catalogTea("XY1000", "Ohne Sorte", teaType = null, basePriceCentsPer100g = 1500),
            // Kept for its mark after it left the range (CAT-18 AC3): only in the *Gemerkt* view.
            catalogTea("XY0170", "Nebelhang Sencha", basePriceCentsPer100g = 1390)
                .copy(originCountry = "Japan", discontinued = true, marked = true),
            // Kräutertees (v0.2.1, D-093): only in the Kräutertee view.
            catalogTea("XK1100", "Wiesenzauber", "Kräutermischungen", 890, thumbnailPath = "wiese")
                .copy(isHerbal = true, isOrganic = true, isNew = true, marked = true),
            catalogTea("XK1200", "Zitronenwiese mit Apfelstückchen", "Kräutermischungen", 790)
                .copy(isHerbal = true, isFlavored = true, owned = true),
            catalogTea("XK1300", "Bergminze", "Einzelkräuter", 1190, thumbnailPath = "minze")
                .copy(isHerbal = true, originCountry = "Deutschland"),
            catalogTea("XK1400", "Abendruhe", "Ayurveda & Wellness", 1290)
                .copy(isHerbal = true, recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT),
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
            packages = listOf(
                CatalogPackage("XY0100-10", "10 g", 10.0, null, false, null, null, null),
                CatalogPackage("XY0100", "100 g", 100.0, 1240, false, null, null, true),
                CatalogPackage("XY0100-250", "250 g · 3 % gespart", 250.0, 3007, true, 3, null, null),
                CatalogPackage("XY0100-1000", "1000 g · 8 % gespart", 1000.0, 11408, true, 8, null, null),
            ),
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

    private fun listState(
        filter: CatalogFilter = CatalogFilter.DEFAULT,
        searchHits: Set<String>? = null,
        refresh: CatalogRefreshState = CatalogRefreshState.Idle,
    ) = CatalogUiState(
        listing = CatalogBrowser.listing(TEAS, filter, searchHits, FakeClock.DEFAULT_WALL_MS),
        filter = filter,
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
            // With the Ratgeber row (CAT-09, #76).
            state = listState().copy(guideCount = 12),
            onLoad = {},
            onShowProgress = {},
            zone = FakeClock.BERLIN,
            thumbnail = { path -> path?.let { remember(it) { placeholderPhoto(it) } } },
        )
    }

    /** A search with the filters changed: by price, „Vorrätig" off, a flavoured hit hidden by „Pur". */
    @Test
    fun listFiltered() = capture("listFiltered") {
        CatalogContent(
            state = listState(
                filter = CatalogFilter(search = "Sencha", sort = CatalogSort.PRICE, availableOnly = false),
                searchHits = setOf("XY0100", "XY0700", "XY0800"),
            ),
            onLoad = {},
            onShowProgress = {},
            zone = FakeClock.BERLIN,
            thumbnail = { path -> path?.let { remember(it) { placeholderPhoto(it) } } },
        )
    }

    /** The Kräutertee view (v0.2.1, #89): the Kräutertees by Sorte, without „Pur" (D-093 Q14). */
    @Test
    fun listHerbal() = capture("listHerbal") {
        CatalogContent(
            state = listState(filter = CatalogFilter.DEFAULT.withHerbal(true)),
            onLoad = {},
            onShowProgress = {},
            zone = FakeClock.BERLIN,
            thumbnail = { path -> path?.let { remember(it) { placeholderPhoto(it) } } },
        )
    }

    /**
     * The Kräutertee view before the first refresh after the update (v0.2.1, #91, D-093 Q20): no
     * Kräutertees yet, the hint with *Jetzt laden* instead of the no-match notice.
     */
    @Test
    fun listHerbalPending() = capture("listHerbalPending") {
        val filter = CatalogFilter.DEFAULT.withHerbal(true)
        val greenOnly = TEAS.filterNot { it.isHerbal }
        CatalogContent(
            state = CatalogUiState(
                listing = CatalogBrowser.listing(greenOnly, filter, null, FakeClock.DEFAULT_WALL_MS),
                filter = filter,
                loading = false,
                herbalPending = true,
            ),
            onLoad = {},
            onShowProgress = {},
            zone = FakeClock.BERLIN,
        )
    }

    /**
     * The *Gemerkt* view (v0.2.2, #117, CAT-18): every marked tea by Sorte, green and herbal, sold
     * out and „Nicht mehr im Sortiment"; only its chip, the header's count.
     */
    @Test
    fun listMarked() = capture("listMarked") {
        CatalogContent(
            state = listState(filter = CatalogFilter.DEFAULT.copy(marked = true)),
            onLoad = {},
            onShowProgress = {},
            zone = FakeClock.BERLIN,
            thumbnail = { path -> path?.let { remember(it) { placeholderPhoto(it) } } },
        )
    }

    /** The *Gemerkt* view without a mark (CAT-18): „Noch nichts gemerkt · Tippe bei einem Tee auf 🔖". */
    @Test
    fun listMarkedEmpty() = capture("listMarkedEmpty") {
        val filter = CatalogFilter.DEFAULT.copy(marked = true)
        CatalogContent(
            state = CatalogUiState(
                listing = CatalogBrowser.listing(
                    TEAS.map { it.copy(marked = false) },
                    filter,
                    null,
                    FakeClock.DEFAULT_WALL_MS,
                ),
                filter = filter,
                loading = false,
            ),
            onLoad = {},
            onShowProgress = {},
            zone = FakeClock.BERLIN,
        )
    }

    /** The Kräutertee view's filter sheet (v0.2.1, #91, D-093 Q19): no Aroma, no Umami; font scale 1.0 only. */
    @Test
    fun filterSheetHerbal() {
        assumeTrue(fontScale == 1f)
        capture("filterSheetHerbal") {
            val state = listState(filter = CatalogFilter.DEFAULT.withHerbal(true).copy(organicOnly = true))
            CatalogContent(state = state, onLoad = {}, onShowProgress = {}, zone = FakeClock.BERLIN)
            CatalogFilterSheet(filter = state.filter, listing = state.listing, onFilterChange = {
            }, onDismissRequest = {})
        }
    }

    /** The filter sheet over the list, at font scale 1.0 only. */
    @Test
    fun filterSheet() {
        assumeTrue(fontScale == 1f)
        capture("filterSheet") {
            val state = listState(filter = CatalogFilter(types = setOf("Gyokuro"), minUmami = UmamiLevel.HIGH))
            CatalogContent(state = state, onLoad = {}, onShowProgress = {}, zone = FakeClock.BERLIN)
            CatalogFilterSheet(filter = state.filter, listing = state.listing, onFilterChange = {
            }, onDismissRequest = {})
        }
    }

    @Test
    fun detail() = capture("detail") { Detail() }

    /** The detail scrolled to its end: description and ZUBEREITUNG. */
    @Test
    fun detailEnd() = capture("detailEnd", scrollToText = "Angaben des Shops, unverändert übernommen.") { Detail() }

    /** ABGELEITETES REZEPT with „Rezept prüfen" and its reason (CAT-04, CAT-05). */
    @Test
    fun detailRecipes() = capture("detailRecipes", scrollToText = "Fehlende Werte aus den Standardwerten der Sorte") {
        // The shop's text contradicts its table (75 °C against 60 °C), as the reason says.
        Detail(
            DETAIL.copy(
                recipeCheckReason = RecipeCheckReason.TABLE_TEXT_CONFLICT,
                brewText = "Zubereitung: 4 g auf 100 ml bei 75 °C, der erste Aufguss 90 Sekunden.",
            ),
        )
    }

    /** The detail scrolled to PACKUNGEN (#67): a sample, the page's own size and two estimates. */
    @Test
    fun detailPackages() = capture("detailPackages", scrollToText = ESTIMATE_NOTE) { Detail() }

    /** SCR-23 over the detail (#71): the size whose price the shop showed, at font scale 1.0. */
    @Test
    fun addSheet() {
        assumeTrue(fontScale == 1f)
        capture("addSheet") {
            Detail()
            CatalogAddSheet(
                state = AddSheetState.open(DETAIL.name, DETAIL.packages, repurchase = false, null, PURCHASE_DAY),
                actions = AddSheetActions(),
                onDismissRequest = {},
            )
        }
    }

    /** *Nachkauf erfassen* (#71): the estimate kept after the shop gave no exact price, a day picked. */
    @Test
    fun repurchaseSheet() {
        assumeTrue(fontScale == 1f)
        capture("repurchaseSheet") {
            Detail(owned = true)
            CatalogAddSheet(
                state = AddSheetState.open(DETAIL.name, DETAIL.packages, repurchase = true, 250.0, PURCHASE_DAY)
                    .fetched(2, null)
                    .pickDate(PURCHASE_DAY.minusDays(3)),
                actions = AddSheetActions(),
                onDismissRequest = {},
            )
        }
    }

    /** The detail of a marked tea (CAT-18): „Gemerkt", the bookmark filled. */
    @Test
    fun detailMarked() = capture("detailMarked") { Detail(marked = true) }

    @Composable
    private fun Detail(tea: CatalogTeaDetail = DETAIL, owned: Boolean = false, marked: Boolean = false) {
        CatalogTeaScreen(
            state = CatalogTeaUiState(
                loading = false,
                tea = tea,
                shopUrl = tea.productUrl,
                ownedTeaId = "own".takeIf { owned },
                marked = marked,
            ),
            onBack = {},
            onOpenShop = {},
            photo = remember { placeholderPhoto("detail") },
        )
    }

    @Test
    fun refreshRunning() = sheet("refreshRunning", CatalogRefreshState.Running(CatalogRefreshProgress(412, 1560, 96)))

    /** The first load: the green teas and the recipes to check (CAT-07 AC3). */
    @Test
    fun refreshSuccess() = sheet(
        "refreshSuccess",
        CatalogRefreshState.Finished(CatalogRefreshOutcome.Success(teas = 274, newTeas = 274, recipesToCheck = 31)),
    )

    /** A later refresh's summary (CAT-07 AC3, #70): every kind of change. */
    @Test
    fun refreshSummary() = sheet(
        "refreshSummary",
        CatalogRefreshState.Finished(
            CatalogRefreshOutcome.Success(
                teas = 276,
                newTeas = 12,
                changedTeas = 5,
                discontinuedTeas = 2,
                recipesToCheck = 3,
            ),
        ),
    )

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

    // *Link einfügen* and a shared link (v0.2.1, #98; CAT-17, SCR-19, 03 §5; spec #81 seam 5).

    /** The paste field with a link from the clipboard, also at font scale 2.0 (its label is long). */
    @Test
    fun linkPaste() = linkSheet("linkPaste", TeaLinkSheetState(text = TEA_LINK), anyFontScale = true)

    @Test
    fun linkLoading() = linkSheet("linkLoading", TeaLinkSheetState(text = TEA_LINK, loading = true))

    @Test
    fun linkNotATeaLink() = linkSheet(
        "linkNotATeaLink",
        TeaLinkSheetState(text = "https://www.example.com/tee/sencha", message = TeaLinkOutcome.NotATeaLink),
        anyFontScale = true,
    )

    @Test
    fun linkNotInCatalog() =
        linkSheet("linkNotInCatalog", TeaLinkSheetState(text = TEA_LINK, message = TeaLinkOutcome.NotInCatalog))

    @Test
    fun linkSharedLoading() = linkSheet("linkSharedLoading", TeaLinkSheetState(shared = true, loading = true))

    @Test
    fun linkSharedOffline() =
        linkSheet("linkSharedOffline", TeaLinkSheetState(shared = true, message = TeaLinkOutcome.Offline))

    @Test
    fun linkSharedRefreshRunning() = linkSheet(
        "linkSharedRefreshRunning",
        TeaLinkSheetState(shared = true, message = TeaLinkOutcome.RefreshRunning),
    )

    /** The tea link's sheet over the list, at font scale 1.0 unless [anyFontScale]. */
    private fun linkSheet(name: String, state: TeaLinkSheetState, anyFontScale: Boolean = false) {
        assumeTrue(anyFontScale || fontScale == 1f)
        capture(name) {
            CatalogContent(
                state = listState(),
                onLoad = {},
                onShowProgress = {},
                thumbnail = { path -> path?.let { remember(it) { placeholderPhoto(it) } } },
            )
            TeaLinkSheet(state = state, onTextChange = {}, onLoad = {}, onDismissRequest = {})
        }
    }
}

/** An invented tea link (D-016). */
private const val TEA_LINK = "https://www.tee-kontor-kiel.de/nebelhang-sencha/XY0170"
