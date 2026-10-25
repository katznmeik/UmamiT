package com.umamit.core.data.catalog

import android.content.Context
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogPackageEntity
import com.umamit.core.database.entity.CatalogTeaEntity
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
import java.io.File
import javax.inject.Inject

/** The local catalog for the screens (SCR-19, SCR-21, CAT-10, CAT-11, CAT-13): read only; only a refresh writes it. */
interface CatalogRepository {
    /** The teas of the shop's current range by name, and the date of the last successful refresh. */
    fun observeCatalog(): Flow<CatalogOverview>

    /** One catalog tea with everything its detail shows (SCR-21), or null when the catalog has none with [key]. */
    fun observeTea(key: String): Flow<CatalogTeaDetail?>
}

/** [CatalogRepository] on `catalog.db` and the photo files of `filesDir/catalog/`. */
class RoomCatalogRepository @Inject constructor(private val dao: CatalogDao, private val photos: CatalogPhotoFiles) :
    CatalogRepository {
    override fun observeCatalog(): Flow<CatalogOverview> = combine(
        dao.observeList(),
        dao.observeLastSuccess(),
    ) { rows, last ->
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
                )
            },
            lastRefreshAtMillis = last,
        )
    }.distinctUntilChanged()

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
    abstract fun catalogRefreshRunner(impl: AppCatalogRefreshRunner): CatalogRefreshRunner

    companion object {
        /** The catalog's photos in app-private `filesDir/catalog/` (05 §4.1, CAT-08); not backed up (05 §8). */
        @Provides
        fun catalogPhotoFiles(@ApplicationContext context: Context): CatalogPhotoFiles =
            CatalogPhotoFiles(File(context.filesDir, CatalogPhotoFiles.DIRECTORY))
    }
}
