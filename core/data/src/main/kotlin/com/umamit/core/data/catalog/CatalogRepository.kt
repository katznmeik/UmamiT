package com.umamit.core.data.catalog

import android.content.Context
import com.umamit.core.catalog.parser.TeeKontorParser
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.dao.TeaDao
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.domain.catalog.CatalogBrowser
import com.umamit.core.model.CatalogFilter
import com.umamit.core.model.CatalogListing
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogPackage
import com.umamit.core.model.CatalogTeaDetail
import com.umamit.core.model.CatalogTeaSummary
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

/** The local catalog for the screens (SCR-19, SCR-21, CAT-10, CAT-11, CAT-13): read only; only a refresh writes it. */
interface CatalogRepository {
    /** The teas of the shop's current range by name, and the date of the last successful refresh. */
    fun observeCatalog(): Flow<CatalogOverview>

    /** The catalog list as [filter] shows it (CAT-10, SCR-19): search, filters, sort and grouping. */
    fun observeListing(filter: CatalogFilter): Flow<CatalogListing>

    /** When the last successful refresh finished (SET-23, CAT-07 AC6); null before the first. */
    fun observeLastRefresh(): Flow<Long?>

    /** One catalog tea with everything its detail shows (SCR-21), or null when the catalog has none with [key]. */
    fun observeTea(key: String): Flow<CatalogTeaDetail?>

    /**
     * The Kräutertees are still to come (v0.2.1, D-093 Q20): the last successful refresh ran a
     * parser from before them, so the first refresh after the update, which brings them once, is
     * still due. False before the first refresh (the empty catalog has its own state).
     */
    fun observeHerbalPending(): Flow<Boolean>
}

/** The catalog keys of the collection's teas: `tea.catalog_key` in `umamit.db` (05 §1.6). */
fun interface OwnedCatalogKeys {
    /**
     * The keys any collection tea links to, archived ones included: the refresher keeps a
     * discontinued catalog tea for them (CAT-07 AC4).
     */
    fun observe(): Flow<Set<String>>

    /** The keys of the teas that are not archived: „im Besitz" (CAT-10, TEA-04). */
    fun observeOwned(): Flow<Set<String>> = observe()
}

/** [OwnedCatalogKeys] from the collection's database. */
class RoomOwnedCatalogKeys @Inject constructor(private val teas: TeaDao) : OwnedCatalogKeys {
    override fun observe(): Flow<Set<String>> = teas.observeCatalogKeys().map { it.toSet() }.distinctUntilChanged()

    override fun observeOwned(): Flow<Set<String>> =
        teas.observeOwnedCatalogKeys().map { it.toSet() }.distinctUntilChanged()
}

/** [CatalogRepository] on `catalog.db`, the photo files of `filesDir/catalog/` and the [owned] keys. */
class RoomCatalogRepository @Inject constructor(
    private val dao: CatalogDao,
    private val photos: CatalogPhotoFiles,
    private val owned: OwnedCatalogKeys = OwnedCatalogKeys { flowOf(emptySet()) },
) : CatalogRepository {
    override fun observeCatalog(): Flow<CatalogOverview> = combine(
        dao.observeList(),
        dao.observeLastSuccess(),
        owned.observeOwned(),
    ) { rows, last, ownedKeys ->
        CatalogOverview(
            teas = rows.map { row ->
                CatalogTeaSummary(
                    key = row.key,
                    name = row.name ?: row.key,
                    secondaryName = row.secondaryName,
                    teaType = row.teaType,
                    basePriceCentsPer100g = row.basePriceCentsPer100g,
                    available = row.available,
                    thumbnailPath = row.photoFile?.let { photos.thumbnail(row.key).path },
                    originCountry = row.originCountry,
                    isOrganic = row.isOrganic,
                    isFlavored = row.isFlavored,
                    umamiLeaves = CatalogColumns.tasteScales(row.tasteScalesJson)
                        .firstOrNull { it.label.equals(UMAMI_SCALE, ignoreCase = true) }?.leaves,
                    recipeCheckReason = row.recipeCheckReason,
                    isNew = row.isNew,
                    owned = row.key in ownedKeys,
                    isHerbal = row.styleKey == HERBAL_STYLE,
                )
            },
            lastRefreshAtMillis = last,
        )
    }.distinctUntilChanged()

    override fun observeListing(filter: CatalogFilter): Flow<CatalogListing> {
        val hits: Flow<Set<String>?> = CatalogBrowser.ftsQuery(filter.search)
            ?.let { match -> dao.observeSearch(match).map { it.toSet() } }
            ?: flowOf(null)
        return combine(observeCatalog(), hits) { catalog, found ->
            CatalogBrowser.listing(catalog.teas, filter, found, catalog.lastRefreshAtMillis)
        }.distinctUntilChanged()
    }

    private companion object {
        /** The shop's name of the umami scale (08 §2.3). */
        const val UMAMI_SCALE = "Umami"

        /** The style of the shop's Kräutertees (08 §3, D-093): a herbal tea is told apart by it. */
        const val HERBAL_STYLE = "herbal"
    }

    override fun observeLastRefresh(): Flow<Long?> = dao.observeLastSuccess().distinctUntilChanged()

    override fun observeHerbalPending(): Flow<Boolean> = dao.observeLastSuccessParserVersion()
        .map { version -> version != null && version < TeeKontorParser.HERBAL_SINCE_VERSION }
        .distinctUntilChanged()

    override fun observeTea(key: String): Flow<CatalogTeaDetail?> = combine(
        dao.observeTea(key),
        dao.observePackages(key),
    ) { tea, packages -> tea?.toDetail(packages) }.distinctUntilChanged()

    private fun CatalogTeaEntity.toDetail(packages: List<CatalogPackageEntity>) = CatalogTeaDetail(
        key = key,
        name = name ?: key,
        secondaryName = secondaryName,
        productUrl = productUrl,
        teaType = teaType,
        shopCategory = shopCategory,
        originCountry = originCountry,
        originRegion = originRegion,
        cultivar = cultivar,
        harvest = harvest,
        shading = shading,
        steaming = steaming,
        isOrganic = isOrganic,
        isFlavored = isFlavored,
        coldBrewSuitable = coldBrewSuitable,
        aroma = aroma,
        tasteNotes = tasteNotes,
        manufacturer = manufacturer,
        season = season,
        matchaGrade = matchaGrade,
        tasteScales = CatalogColumns.tasteScales(tasteScalesJson),
        ingredients = ingredients,
        descriptionHtml = descriptionHtml,
        brewTable = CatalogColumns.brewTable(brewTableRaw),
        brewText = brewTextRaw,
        recipes = DerivedRecipesColumn.recipes(derivedRecipesJson),
        recipeCheckReason = recipeCheckReason,
        basePriceCentsPer100g = basePriceCentsPer100g,
        available = available,
        discontinued = discontinued,
        photoPath = photoFile?.let { photos.photo(key).path },
        packages = packages.map {
            CatalogPackage(
                articleNumber = it.articleNumber,
                label = it.label,
                grams = it.grams,
                priceCents = it.priceCents,
                priceIsEstimate = it.priceIsEstimate,
                savingsPercent = it.savingsPercent,
                variantUrl = it.variantUrl,
                available = it.available,
            )
        },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogModule {
    @Binds
    abstract fun catalogRepository(impl: RoomCatalogRepository): CatalogRepository

    @Binds
    abstract fun ownedCatalogKeys(impl: RoomOwnedCatalogKeys): OwnedCatalogKeys

    @Binds
    abstract fun catalogRefreshRunner(impl: AppCatalogRefreshRunner): CatalogRefreshRunner

    /** The same runner, as the refresh service sees it (the app hands it over, [CatalogRefreshHost]). */
    @Binds
    abstract fun catalogRefreshServicePort(impl: AppCatalogRefreshRunner): CatalogRefreshServicePort

    @Binds
    abstract fun catalogRefreshForeground(impl: ServiceCatalogRefreshForeground): CatalogRefreshForeground

    /** CAT-15 after a successful refresh. */
    @Binds
    abstract fun catalogSeedLinker(impl: RoomCatalogSeedLinker): CatalogSeedLinker

    @Binds
    abstract fun seedTeaLinkMarker(impl: DataStoreSeedTeaLinkMarker): SeedTeaLinkMarker

    companion object {
        /** The catalog's photos in app-private `filesDir/catalog/` (05 §4.1, CAT-08); not backed up (05 §8). */
        @Provides
        fun catalogPhotoFiles(@ApplicationContext context: Context): CatalogPhotoFiles =
            CatalogPhotoFiles(File(context.filesDir, CatalogPhotoFiles.DIRECTORY))
    }
}
