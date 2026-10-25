package com.umamit.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.database.CatalogDatabase
import com.umamit.core.database.entity.CatalogRefreshLogEntity
import com.umamit.core.database.entity.CatalogRefreshStatus
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.database.entity.StagedCatalogTeaEntity
import com.umamit.core.model.CatalogFilter
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
    private val repository by lazy { RoomCatalogRepository(dao, CatalogPhotoFiles(folder.newFolder()), owned) }

    @After
    fun tearDown() = db.close()

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
    ) = CatalogTeaEntity(
        key = key,
        name = name,
        secondaryName = null,
        productUrl = "https://shop.example/t/$key",
        shopCategory = null,
        teaType = type,
        originCountry = country,
        originRegion = region,
        cultivar = cultivar,
        isOrganic = organic,
        isFlavored = flavored,
        tasteScalesJson = umami?.let { """{"Umami":$it,"Bitterstoffe":1}""" },
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

    private suspend fun browse(filter: CatalogFilter = CatalogFilter.DEFAULT): CatalogListing =
        repository.observeListing(filter).first()

    private suspend fun names(filter: CatalogFilter = CatalogFilter.DEFAULT) = browse(filter).teas.map { it.name }

    private val all = CatalogFilter(flavour = FlavourFilter.ALL, availableOnly = false, sort = CatalogSort.NAME)

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

        assertThat(names(CatalogFilter(flavour = FlavourFilter.ALL))).containsExactly("Kirschduft", "Morgennebel")
        assertThat(names(CatalogFilter(flavour = FlavourFilter.FLAVOURED))).containsExactly("Kirschduft")
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

        val listing = browse(CatalogFilter(search = "sencha"))

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
    fun anEmptyCatalogListsNothing() = runTest {
        val listing = browse()

        assertThat(listing.totalCount).isEqualTo(0)
        assertThat(listing.sections).isEmpty()
        assertThat(listing.lastRefreshAtMillis).isNull()
    }
}
