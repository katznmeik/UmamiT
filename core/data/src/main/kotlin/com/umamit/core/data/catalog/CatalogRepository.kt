package com.umamit.core.data.catalog

import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.model.CatalogOverview
import com.umamit.core.model.CatalogTeaSummary
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** The local catalog for the screens (SCR-19, CAT-10, CAT-13): read only; only a refresh writes it. */
interface CatalogRepository {
    /** The teas of the shop's current range by name, and the date of the last successful refresh. */
    fun observeCatalog(): Flow<CatalogOverview>
}

/** [CatalogRepository] on `catalog.db`. */
class RoomCatalogRepository @Inject constructor(private val dao: CatalogDao) : CatalogRepository {
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
                )
            },
            lastRefreshAtMillis = last,
        )
    }.distinctUntilChanged()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogModule {
    @Binds
    abstract fun catalogRepository(impl: RoomCatalogRepository): CatalogRepository

    @Binds
    abstract fun catalogRefreshRunner(impl: AppCatalogRefreshRunner): CatalogRefreshRunner
}
