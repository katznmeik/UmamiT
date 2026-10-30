package com.umamit.core.data.catalog

import com.umamit.core.common.WallClock
import com.umamit.core.database.dao.CatalogBookmarkDao
import com.umamit.core.database.entity.CatalogBookmarkEntity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The Merkliste (CAT-18, 05 §3.13, D-097): catalog teas marked for later by their catalog key, without adding them
 * to the collection. Personal data in `umamit.db`, so a catalog refresh never changes it.
 */
interface CatalogBookmarkRepository {
    /** The catalog keys of the marked teas. */
    fun observeMarked(): Flow<Set<String>>

    suspend fun isMarked(catalogKey: String): Boolean

    /** *Merken*: marks the tea; a tea that is already marked keeps its mark. */
    suspend fun mark(catalogKey: String)

    /** Removes the tea's mark; a tea without one is ignored. */
    suspend fun unmark(catalogKey: String)
}

/** [CatalogBookmarkRepository] on `catalog_bookmark`, marked at the [clock]'s time. */
class RoomCatalogBookmarkRepository @Inject constructor(
    private val dao: CatalogBookmarkDao,
    private val clock: WallClock,
) : CatalogBookmarkRepository {
    override fun observeMarked(): Flow<Set<String>> = dao.observeKeys().map { it.toSet() }.distinctUntilChanged()

    override suspend fun isMarked(catalogKey: String): Boolean = dao.isMarked(catalogKey)

    override suspend fun mark(catalogKey: String) = dao.insert(CatalogBookmarkEntity(catalogKey, clock.nowMs()))

    override suspend fun unmark(catalogKey: String) = dao.delete(catalogKey)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogBookmarkModule {
    @Binds
    abstract fun catalogBookmarkRepository(impl: RoomCatalogBookmarkRepository): CatalogBookmarkRepository
}
