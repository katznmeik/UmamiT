package com.umamit.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.InventoryEventEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.database.entity.SessionStepEntity
import kotlinx.coroutines.flow.Flow

/** A finished session row with its step rows and descriptor rows, both in any order (INS-01–INS-05). */
data class StatsSessionRow(
    @Embedded val session: BrewSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id")
    val steps: List<SessionStepEntity>,
    @Relation(parentColumn = "id", entityColumn = "session_id")
    val descriptors: List<SessionDescriptorEntity>,
)

/** A tea as the statistics read it; [remainingGrams] is the Σ of its stock events, null without any event. */
data class StatsTeaRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "low_stock_grams") val lowStockGrams: Double?,
    @ColumnInfo(name = "low_stock_warning") val lowStockWarning: Boolean,
    @ColumnInfo(name = "remaining_grams") val remainingGrams: Double?,
    @ColumnInfo(name = "cover_photo_id") val coverPhotoId: String?,
    @ColumnInfo(name = "style_key") val styleKey: String?,
)

/** The statistics' read side (INS-01–INS-07, D-115): read-only, no schema change. */
@Dao
interface StatsDao {
    /**
     * Every finished session (`COMPLETED` or `MANUAL`) with its steps and descriptors, also those of a deleted tea
     * (`tea_id` null) or an archived one; a session in progress is left out. Emits again on every write to the
     * sessions, their steps or their descriptors.
     */
    @Transaction
    @Query("SELECT * FROM brew_session WHERE status != 'IN_PROGRESS' ORDER BY started_at")
    fun observeFinishedSessions(): Flow<List<StatsSessionRow>>

    /** Every tea, archived ones included, with the Σ of its stock events. Emits again on every tea or stock write. */
    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.is_archived AS is_archived, t.created_at AS created_at,
            t.low_stock_grams AS low_stock_grams, t.low_stock_warning AS low_stock_warning,
            t.cover_photo_id AS cover_photo_id, t.style_key AS style_key,
            (SELECT SUM(e.delta_grams) FROM inventory_event e WHERE e.tea_id = t.id) AS remaining_grams
        FROM tea t
        """,
    )
    fun observeTeas(): Flow<List<StatsTeaRow>>

    /** The priced INITIAL and PURCHASE events (INS-05); BREW, ADJUSTMENT and unpriced events are left out. */
    @Query(
        "SELECT * FROM inventory_event WHERE type IN ('INITIAL', 'PURCHASE') AND price_cents IS NOT NULL " +
            "ORDER BY occurred_at, created_at",
    )
    fun observePricedPurchases(): Flow<List<InventoryEventEntity>>
}
