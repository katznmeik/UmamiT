package com.umamit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.umamit.core.database.entity.CatalogBookmarkEntity
import kotlinx.coroutines.flow.Flow

/** The Merkliste `catalog_bookmark` in `umamit.db` (05 §3.13, CAT-18). */
@Dao
interface CatalogBookmarkDao {
    /** Marks a catalog tea; a tea that is already marked keeps its row and its first `created_at`. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: CatalogBookmarkEntity)

    /** Removes the mark of [catalogKey]; a tea without one is ignored. */
    @Query("DELETE FROM catalog_bookmark WHERE catalog_key = :catalogKey")
    suspend fun delete(catalogKey: String)

    /** The marked catalog keys, by key. */
    @Query("SELECT catalog_key FROM catalog_bookmark ORDER BY catalog_key")
    fun observeKeys(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM catalog_bookmark WHERE catalog_key = :catalogKey)")
    suspend fun isMarked(catalogKey: String): Boolean
}
