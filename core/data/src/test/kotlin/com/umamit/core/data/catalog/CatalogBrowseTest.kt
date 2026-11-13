package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.TestDatabase
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogKind
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogSort
import com.umamit.core.model.FlavourFilter
import com.umamit.core.model.RecipeCheckReason
import com.umamit.core.model.UmamiLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Finding teas (CAT-10, #69) on an in-memory catalog database: the default view (D-073), every
 * filter, each sort, search over the FTS columns, the „Neu" set (D-083) and the owned flag. The
 * teas are invented (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogBrowseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val db = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CatalogDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.catalogDao()
    private val owned = FakeOwnedCatalogKeys()

    /** `umamit.db` with the Merkliste (CAT-18), in memory too. */
    private val umami = TestDatabase()
    private val repository by lazy {
        RoomCatalogRepository(dao, CatalogPhotoFiles(folder.newFolder()), owned, umami.bookmarks)
    }

    @After
    fun tearDown() {
        db.close()
        umami.close()
    }

    private class FakeOwnedCatalogKeys : OwnedCatalogKeys {
        val keys = MutableStateFlow(emptySet<String>())
        override fun observe(): Flow<Set<String>> = keys
    }

    @Suppress("LongParameterList")
    private fun tea(
        key: String,
        name: String,
        type: String? = "Sencha",
        price: Long? = 1000,
        available: Boolean? = true,
        flavored: Boolean = false,
        organic: Boolean = false,
        country: String? = "Japan",
        region: String? = null,
        cultivar: String? = null,
        description: String? = null,
        umami: Int? = null,
        flagged: Boolean = false,
        style: String? = null,
        notes: String? = null,
        scales: String? = umami?.let { """{"Umami":$it,"Bitterstoffe":1}""" },
    ) = CatalogTeaEntity(
        key = key,
        name = name,
        secondaryName = null,
        productUrl = "https://shop.example/t/$key",
        shopCategory = null,
        teaType = type,
        styleKey = style,
        originCountry = country,
        originRegion = region,
        cultivar = cultivar,
        isOrganic = organic,
        isFlavored = flavored,
        tasteScalesJson = scales,
        tasteNotes = notes,
        breadcrumb = null,
        descriptionText = description,
        recipeCheckReason = RecipeCheckReason.FROM_STYLE_DEFAULT.takeIf { flagged },
        basePriceCentsPer100g = price,
        available = available,
        firstSeenAt = 0,
        lastSeenAt = 0,
    )

    private var refreshes = 0L

    /** One successful refresh at [at] that finds exactly [teas] (the swap keeps earlier first sightings). */
    private suspend fun refresh(vararg teas: CatalogTeaEntity, at: Long = ++refreshes * 1_000) {
        teas.forEach { dao.stage(StagedCatalogTeaEntity(it.copy(firstSeenAt = at, lastSeenAt = at))) }
        dao.swap(
            emptyList(),
            CatalogRefreshLogEntity(
                startedAt = at,
                finishedAt = at + 1,
                status = CatalogRefreshStatus.SUCCESS,
                newCount = 0,
                changedCount = 0,
                discontinuedCount = 0,
                flaggedCount = 0,
                photosCount = 0,
                parserVersion = 1,
                errorSummary = null,
            ),
        )
    }

    /** The Grüntee view's defaults: no kind is the list's default since v0.6.1 (D-114). */
    private val greenView = CatalogFilter(kind = CatalogKind.GREEN)

    private suspend fun browse(filter: CatalogFilter = greenView): CatalogListing =
        repository.observeListing(filter).first()

    private suspend fun names(filter: CatalogFilter = greenView) = browse(filter).teas.map { it.name }

    private val all = greenView.copy(flavour = FlavourFilter.ALL, availableOnly = false, sort = CatalogSort.NAME)

    @Test
    fun theDefaultViewGroupsAvailablePureTeasBySorte() = runTest {
        refresh(
            tea("XY0100", "Morgennebel", "Sencha"),
            tea("XY0200", "Abendrot", "Gyokuro"),
            tea("XY0300", "Bergwind", "Sencha"),
            tea("XY0400", "Kirschduft", "Sencha", flavored = true),
            tea("XY0500", "Stiller See", "Gyokuro", available = false),
            tea("XY0600", "Ohne Namen", type = null),
            tea("XY0700", "Unbekannt", "Matcha", available = null),
        )

        val listing = browse()

        assertThat(listing.grouped).isTrue()
        assertThat(listing.sections.map { it.teaType to it.teas.map { tea -> tea.name } }).containsExactly(
            "Gyokuro" to listOf("Abendrot"),
            "Matcha" to listOf("Unbekannt"),
            "Sencha" to listOf("Bergwind", "Morgennebel"),
            null to listOf("Ohne Namen"),
        ).inOrder()
        assertThat(listing.totalCount).isEqualTo(7)
        assertThat(listing.types).containsExactly("Gyokuro", "Matcha", "Sencha").inOrder()
        assertThat(listing.lastRefreshAtMillis).isEqualTo(1_001)
    }

    @Test
    fun oneTapOnPurShowsTheFlavouredTeasToo() = runTest {
        refresh(tea("XY0100", "Morgennebel"), tea("XY0400", "Kirschduft", flavored = true))

        assertThat(names(greenView.copy(flavour = FlavourFilter.ALL))).containsExactly("Kirschduft", "Morgennebel")
        assertThat(names(greenView.copy(flavour = FlavourFilter.FLAVOURED))).containsExactly("Kirschduft")
    }

    @Test
    fun everyFilterNarrowsTheList() = runTest {
        refresh(
            tea("XY0100", "Morgennebel", "Sencha", organic = true, umami = 4),
            tea("XY0200", "Abendrot", "Gyokuro", umami = 5),
            tea("XY0300", "Bergwind", "Bancha", country = "China", umami = 2),
            tea("XY0400", "Talblick", "Sencha", country = "Japan, Korea", umami = 3),
            tea("XY0500", "Stiller See", "Gyokuro", available = false),
        )
        owned.keys.value = setOf("XY0200", "XY0500", "ZZ9999")

        assertThat(names(all.copy(types = setOf("Gyokuro", "Bancha"))))
            .containsExactly("Abendrot", "Bergwind", "Stiller See").inOrder()
        assertThat(names(all.copy(origins = setOf("Korea", "China")))).containsExactly("Bergwind", "Talblick").inOrder()
        assertThat(browse(all).origins).containsExactly("China", "Japan", "Korea").inOrder()
        assertThat(names(all.copy(organicOnly = true))).containsExactly("Morgennebel")
        assertThat(names(all.copy(minUmami = UmamiLevel.HIGH))).containsExactly("Abendrot", "Morgennebel", "Talblick")
            .inOrder()
        assertThat(names(all.copy(minUmami = UmamiLevel.VERY_HIGH))).containsExactly("Abendrot", "Morgennebel")
            .inOrder()
        assertThat(names(all.copy(ownedOnly = true))).containsExactly("Abendrot", "Stiller See").inOrder()
        assertThat(names(all.copy(availableOnly = true))).doesNotContain("Stiller See")
    }

    @Test
    fun theListSortsByNameByPriceAndByType() = runTest {
        refresh(
            tea("XY0100", "Morgennebel", "Sencha", price = 1590),
            tea("XY0200", "abendrot", "Gyokuro", price = 4200),
            tea("XY0300", "Älpler", "Bancha", price = 690),
            tea("XY0400", "Ohne Preis", "Sencha", price = null),
        )

        val byName = browse(all)
        assertThat(byName.grouped).isFalse()
        assertThat(byName.teas.map { it.name }).containsExactly("abendrot", "Älpler", "Morgennebel", "Ohne Preis")
            .inOrder()
        assertThat(names(all.copy(sort = CatalogSort.PRICE)))
            .containsExactly("Älpler", "Morgennebel", "abendrot", "Ohne Preis").inOrder()
        assertThat(browse(all.copy(sort = CatalogSort.TYPE)).sections.map { it.teaType })
            .containsExactly("Bancha", "Gyokuro", "Sencha").inOrder()
    }

    @Test
    fun searchFindsTeasByNameDescriptionCultivarAndRegion() = runTest {
        refresh(
            tea("XY0100", "Morgennebel Sencha", cultivar = "Okumidori"),
            tea("XY0200", "Abendrot", region = "Kagoshima"),
            tea("XY0300", "Bergwind", description = "Ein milder Tee aus dem Hochland."),
            tea("XY0400", "Talblick"),
        )

        assertThat(names(all.copy(search = "Okumidori"))).containsExactly("Morgennebel Sencha")
        assertThat(names(all.copy(search = "kagoshima"))).containsExactly("Abendrot")
        assertThat(names(all.copy(search = "hochl"))).containsExactly("Bergwind")
        assertThat(names(all.copy(search = "morgen sencha"))).containsExactly("Morgennebel Sencha")
        assertThat(names(all.copy(search = "  \"(*"))).hasSize(4)
        assertThat(names(all.copy(search = "Nirgendwo"))).isEmpty()
    }

    @Test
    fun searchIgnoresCaseAndDiacritics() = runTest {
        refresh(tea("XY0100", "Älpler Gokō"), tea("XY0200", "Morgennebel"))

        assertThat(names(all.copy(search = "älpler"))).containsExactly("Älpler Gokō")
        assertThat(names(all.copy(search = "goko"))).containsExactly("Älpler Gokō")
    }

    @Test
    fun searchCountsTheHitsTheFiltersHide() = runTest {
        refresh(
            tea("XY0100", "Kirsch Sencha", flavored = true),
            tea("XY0200", "Morgen Sencha"),
            tea("XY0300", "Abend Sencha", available = false),
        )

        val listing = browse(greenView.copy(search = "sencha"))

        assertThat(listing.teas.map { it.name }).containsExactly("Morgen Sencha")
        assertThat(listing.hiddenMatches).isEqualTo(2)
        assertThat(browse().hiddenMatches).isEqualTo(0)
    }

    @Test
    fun neuMarksTheTeasFirstSeenInTheLatestRefresh() = runTest {
        refresh(tea("XY0100", "Morgennebel"), tea("XY0200", "Abendrot"))
        // The first refresh finds everything: nothing is „Neu" yet (D-083).
        assertThat(browse(all).teas.filter { it.isNew }).isEmpty()

        refresh(tea("XY0100", "Morgennebel"), tea("XY0200", "Abendrot"), tea("XY0300", "Bergwind"))
        assertThat(browse(all).teas.filter { it.isNew }.map { it.name }).containsExactly("Bergwind")
        assertThat(names(all.copy(newOnly = true))).containsExactly("Bergwind")

        // Until the next refresh only.
        refresh(tea("XY0100", "Morgennebel"), tea("XY0200", "Abendrot"), tea("XY0300", "Bergwind"))
        assertThat(names(all.copy(newOnly = true))).isEmpty()
    }

    @Test
    fun aFailedRefreshDoesNotEndNeu() = runTest {
        refresh(tea("XY0100", "Morgennebel"))
        refresh(tea("XY0100", "Morgennebel"), tea("XY0300", "Bergwind"))
        dao.log(
            CatalogRefreshLogEntity(
                startedAt = 9_000,
                finishedAt = 9_001,
                status = CatalogRefreshStatus.FAILED,
                newCount = 0,
                changedCount = 0,
                discontinuedCount = 0,
                flaggedCount = 0,
                photosCount = 0,
                parserVersion = 1,
                errorSummary = "stopped",
            ),
        )

        assertThat(names(all.copy(newOnly = true))).containsExactly("Bergwind")
    }

    @Test
    fun aRowCarriesItsFlags() = runTest {
        refresh(
            tea("XY0100", "Morgennebel", organic = true, flavored = true, umami = 3, flagged = true, country = "Japan"),
        )
        owned.keys.value = setOf("XY0100")

        val tea = browse(all).teas.single()

        assertThat(tea.isOrganic).isTrue()
        assertThat(tea.isFlavored).isTrue()
        assertThat(tea.owned).isTrue()
        assertThat(tea.recipeCheckReason).isEqualTo(RecipeCheckReason.FROM_STYLE_DEFAULT)
        assertThat(tea.umamiLevel).isEqualTo(UmamiLevel.HIGH)
        assertThat(tea.originCountry).isEqualTo("Japan")
    }

    @Test
    fun theFlavourFilterReadsTheShopsGeschmackAndScales() = runTest {
        refresh(
            tea("XY0100", "Morgennebel", notes = "fruchtig, mild, süß", scales = """{"Umami":4,"Süße":3}"""),
            tea("XY0200", "Abendrot", notes = "Mild, grasig", scales = """{"Süße":1,"Bitterstoffe":2}"""),
            tea("XY0300", "Bergwind"),
        )

        val listing = browse(all)
        assertThat(listing.teas.first { it.name == "Morgennebel" }.tasteNotes)
            .containsExactly("fruchtig", "mild", "süß").inOrder()
        assertThat(listing.tasteNotes.first().label).isEqualTo("mild")
        assertThat(listing.tasteScales.map { it.label }).containsExactly("Süße", "Bitterstoffe").inOrder()
        assertThat(names(all.copy(notes = setOf("mild")))).containsExactly("Abendrot", "Morgennebel")
        assertThat(names(all.copy(minScales = mapOf("suße" to 2)))).containsExactly("Morgennebel")
    }

    @Test
    fun aGeschmackNoteListSplitsOnCommasSlashesAndUnd() = runTest {
        // The shop also writes „fruchtig/süß" and „mild und grasig" (08 §2.3 *Geschmack*).
        refresh(tea("XY0100", "Morgennebel", notes = "fruchtig/süß, mild und grasig, Wunderbar"))

        assertThat(browse(all).teas.single().tasteNotes)
            .containsExactly("fruchtig", "süß", "mild", "grasig", "Wunderbar").inOrder()
    }

    /** Two green teas and three Kräutertees (v0.2.1, D-093), one of them flavoured, one sold out. */
    private suspend fun greenAndHerbal() = refresh(
        tea("XY0100", "Morgennebel", "Sencha", umami = 4),
        tea("XY0200", "Kirschduft", "Sencha", flavored = true),
        tea("XK0300", "Wiesenzauber", "Mischungen", country = null, style = HERBAL),
        tea("XK0400", "Zitronenwiese", "Mischungen", flavored = true, country = null, style = HERBAL),
        tea("XK0500", "Bergminze", "Einzelkräuter", available = false, country = "Deutschland", style = HERBAL),
    )

    @Test
    fun kraeuterteesAreHiddenInTheDefaultViewAndTheirViewShowsOnlyThemBySorte() = runTest {
        greenAndHerbal()

        val green = browse()
        assertThat(green.teas.map { it.name }).containsExactly("Morgennebel")
        assertThat(green.totalCount).isEqualTo(2)
        assertThat(green.types).containsExactly("Sencha")

        // The chip (SCR-19): grouped by Sorte; „Vorrätig" still applies, „Pur" does not (Q14).
        val herbal = browse(CatalogFilter(kind = CatalogKind.HERBAL))
        assertThat(herbal.sections.map { it.teaType to it.teas.map { tea -> tea.name } }).containsExactly(
            "Mischungen" to listOf("Wiesenzauber", "Zitronenwiese"),
        )
        assertThat(herbal.teas.all { (it.kind == CatalogKind.HERBAL) }).isTrue()
        assertThat(herbal.totalCount).isEqualTo(3)
        assertThat(herbal.catalogCount).isEqualTo(5)
        assertThat(herbal.types).containsExactly("Einzelkräuter", "Mischungen").inOrder()
        assertThat(herbal.origins).containsExactly("Deutschland")
        assertThat(names(CatalogFilter(kind = CatalogKind.HERBAL, availableOnly = false, sort = CatalogSort.NAME)))
            .containsExactly("Bergminze", "Wiesenzauber", "Zitronenwiese").inOrder()
    }

    @Test
    fun theFlavourAndUmamiFiltersDoNotApplyInTheKraeuterteeView() = runTest {
        greenAndHerbal()

        val herbal = CatalogFilter(kind = CatalogKind.HERBAL, sort = CatalogSort.NAME)
        assertThat(names(herbal.copy(flavour = FlavourFilter.PURE))).containsExactly("Wiesenzauber", "Zitronenwiese")
        assertThat(names(herbal.copy(flavour = FlavourFilter.FLAVOURED)))
            .containsExactly("Wiesenzauber", "Zitronenwiese")
        assertThat(names(herbal.copy(minUmami = UmamiLevel.HIGH))).containsExactly("Wiesenzauber", "Zitronenwiese")
        // In the green view they still do.
        assertThat(names(greenView.copy(minUmami = UmamiLevel.HIGH))).containsExactly("Morgennebel")
    }

    @Test
    fun searchHitsOfTheOtherKindCountAsHiddenByTheFilters() = runTest {
        greenAndHerbal()

        // CAT-10: „Wiesen" finds a Kräutertee, hidden in the green view, and a green tea's „Kirsch" in the other.
        val green = browse(greenView.copy(search = "wiesen"))
        assertThat(green.teas).isEmpty()
        assertThat(green.hiddenMatches).isEqualTo(1)
        assertThat(green.hiddenInOtherView).isEqualTo(1)

        val herbal = browse(CatalogFilter(search = "kirsch", kind = CatalogKind.HERBAL))
        assertThat(herbal.hiddenMatches).isEqualTo(1)
        assertThat(herbal.hiddenInOtherView).isEqualTo(1)
    }

    @Test
    fun theKraeuterteeChipDoesNotCountAsAChangedFilterAndNeitherDoesPurInItsView() {
        assertThat(CatalogFilter.DEFAULT.changedFilters).isEqualTo(0)
        val herbal = CatalogFilter.DEFAULT.withKind(CatalogKind.HERBAL)
        assertThat(herbal.kind).isEqualTo(CatalogKind.HERBAL)
        // The kind doesn't count in „Filter (n)" since v0.6.1 (D-114).
        assertThat(herbal.changedFilters).isEqualTo(0)
        assertThat(herbal.copy(flavour = FlavourFilter.ALL, minUmami = UmamiLevel.HIGH).changedFilters).isEqualTo(0)
        // Switching drops the Sorten of the other kind and keeps search, sort and the rest.
        val switched = CatalogFilter(
            search = "x",
            types = setOf("Sencha"),
            organicOnly = true,
        ).withKind(CatalogKind.HERBAL)
        assertThat(switched).isEqualTo(CatalogFilter(search = "x", organicOnly = true, kind = CatalogKind.HERBAL))
        assertThat(switched.withKind(CatalogKind.GREEN).kind).isEqualTo(CatalogKind.GREEN)
        // *Alle zeigen* clears the kind (D-114).
        assertThat(herbal.withoutFilters().kind).isNull()
    }

    @Test
    fun anEmptyCatalogListsNothing() = runTest {
        val listing = browse()

        assertThat(listing.totalCount).isEqualTo(0)
        assertThat(listing.catalogCount).isEqualTo(0)
        assertThat(listing.sections).isEmpty()
        assertThat(listing.lastRefreshAtMillis).isNull()
    }

    /** The catalog of the Merkliste tests: a later refresh found every tea but Nebelhang, kept for its mark. */
    private suspend fun merklisteCatalog() {
        val nebelhang = tea("XY0170", "Nebelhang", "Sencha")
        val teas = arrayOf(
            tea("XY0100", "Nebelmorgen", "Sencha"),
            tea("XY0200", "Abendrot", "Gyokuro"),
            tea("XY0400", "Kirschduft", "Sencha", flavored = true),
            tea("XY0500", "Stiller See", "Gyokuro", available = false),
            tea("XK0300", "Wiesenzauber", "Mischungen", country = null, style = HERBAL),
        )
        refresh(*teas, nebelhang)
        teas.forEach { dao.stage(StagedCatalogTeaEntity(it.copy(firstSeenAt = 1_000, lastSeenAt = 2_000))) }
        dao.swap(
            emptyList(),
            CatalogRefreshLogEntity(
                startedAt = 2_000,
                finishedAt = 2_001,
                status = CatalogRefreshStatus.SUCCESS,
                newCount = 0,
                changedCount = 0,
                discontinuedCount = 1,
                flaggedCount = 0,
                photosCount = 0,
                parserVersion = 1,
                errorSummary = null,
            ),
            discontinued = listOf("XY0170"),
        )
    }

    @Test
    fun gemerktShowsEveryMarkedTeaWhateverTheFilters() = runTest {
        // CAT-18 AC1: green and herbal, flavoured, sold out and „Nicht mehr im Sortiment", grouped by Sorte.
        merklisteCatalog()
        listOf("XY0170", "XY0200", "XY0400", "XY0500", "XK0300").forEach { umami.bookmarks.mark(it) }
        // A filter set before the chip doesn't apply in its view.
        val filter = CatalogFilter(organicOnly = true, types = setOf("Sencha"), marked = true)

        val listing = browse(filter)

        assertThat(listing.grouped).isTrue()
        assertThat(listing.sections.map { it.teaType to it.teas.map { tea -> tea.name } }).containsExactly(
            "Gyokuro" to listOf("Abendrot", "Stiller See"),
            "Mischungen" to listOf("Wiesenzauber"),
            "Sencha" to listOf("Kirschduft", "Nebelhang"),
        ).inOrder()
        assertThat(listing.teas.all { it.marked }).isTrue()
        assertThat(listing.teas.single { it.key == "XY0170" }.discontinued).isTrue()
        // The header's count („… · 5 gemerkt · …"); nothing is hidden in this view.
        assertThat(listing.totalCount).isEqualTo(5)
        assertThat(listing.hiddenMatches).isEqualTo(0)
        // Outside the view the discontinued tea stays hidden and the rows know their marks.
        val default = browse()
        assertThat(default.teas.map { it.name }).containsExactly("Abendrot", "Nebelmorgen").inOrder()
        assertThat(default.teas.map { it.key to it.marked }).containsExactly("XY0200" to true, "XY0100" to false)
            .inOrder()
        assertThat(browse(all).totalCount).isEqualTo(4)
    }

    @Test
    fun theSearchNarrowsGemerkt() = runTest {
        merklisteCatalog()
        listOf("XY0170", "XY0200", "XK0300").forEach { umami.bookmarks.mark(it) }

        val listing = browse(CatalogFilter(search = "nebel", marked = true))

        // Nebelmorgen matches too, but it isn't marked: it is no hidden hit of this view.
        assertThat(listing.teas.map { it.name }).containsExactly("Nebelhang")
        assertThat(listing.totalCount).isEqualTo(3)
        assertThat(listing.hiddenMatches).isEqualTo(0)
        assertThat(browse(CatalogFilter(search = "Nirgendwo", marked = true)).teas).isEmpty()
    }

    @Test
    fun nothingMarkedLeavesGemerktEmpty() = runTest {
        merklisteCatalog()
        // A mark whose tea is gone (never expected, 05 §3.13) is ignored.
        umami.bookmarks.mark("ZZ9999")

        val listing = browse(CatalogFilter(marked = true))

        assertThat(listing.sections).isEmpty()
        assertThat(listing.totalCount).isEqualTo(0)
        assertThat(listing.catalogCount).isEqualTo(5)
    }

    private companion object {
        const val HERBAL = "herbal"
    }
}
