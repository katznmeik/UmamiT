package com.umamit.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.SessionDescriptorEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.Flow

/**
 * A session row with its step rows (in any order; sort by `position`), its descriptor rows (in any order; sort by
 * `created_at`) and its tea's cover photo id.
 */
data class JournalSessionRow(
    @Embedded val session: BrewSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id")
    val steps: List<SessionStepEntity>,
    @Relation(parentColumn = "id", entityColumn = "session_id")
    val descriptors: List<SessionDescriptorEntity>,
    @ColumnInfo(name = "tea_cover_photo_id") val teaCoverPhotoId: String?,
)

/**
 * Row access to `brew_session` and `session_step` (05 §3.6, §3.7). The lifecycle and its
 * transactions are in core:data (`RoomSessionRepository`).
 */
@Suppress("TooManyFunctions") // one query per row operation of the session lifecycle (05 §3.6, §3.7)
@Dao
interface SessionDao {
    @Query("SELECT * FROM brew_session WHERE id = :id")
    suspend fun get(id: String): BrewSessionEntity?

    @Query("SELECT * FROM brew_session WHERE status != 'IN_PROGRESS' ORDER BY started_at DESC")
    fun observeFinished(): Flow<List<BrewSessionEntity>>

    /** The sessions still in progress: the active one, and any left behind (a device restore, E11). */
    @Query("SELECT id FROM brew_session WHERE status = 'IN_PROGRESS'")
    suspend fun inProgressIds(): List<String>

    @Insert
    suspend fun insert(session: BrewSessionEntity)

    @Update
    suspend fun update(session: BrewSessionEntity)

    /** Deletes the session; its steps, its stock event and the active brew go with it (FK CASCADE). */
    @Query("DELETE FROM brew_session WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Deletes a finished session as [delete] does; a session still in progress is left alone, so the journal's
     * *Löschen* can never touch the running timer's row (JRN-10, D-102).
     */
    @Query("DELETE FROM brew_session WHERE id = :id AND status != 'IN_PROGRESS'")
    suspend fun deleteFinished(id: String)

    @Query("SELECT * FROM session_step WHERE session_id = :sessionId ORDER BY position")
    suspend fun steps(sessionId: String): List<SessionStepEntity>

    @Insert
    suspend fun insertSteps(steps: List<SessionStepEntity>)

    @Update
    suspend fun updateSteps(steps: List<SessionStepEntity>)

    /** Deletes the rows of [sessionId] from [position] on: the pending suffix (05 §3.7). */
    @Query("DELETE FROM session_step WHERE session_id = :sessionId AND position >= :position")
    suspend fun deleteStepsFrom(sessionId: String, position: Int)
}

/** A tea's average rating over its rated sessions and their count (TEA-08, TEA-13). */
data class TeaRatingRow(
    @ColumnInfo(name = "tea_id") val teaId: String,
    @ColumnInfo(name = "average") val average: Double,
    @ColumnInfo(name = "count") val count: Int,
)

/** A tea's taste averages, each over the sessions that set that axis; null when none did (JRN-03). */
data class TasteAveragesRow(
    @ColumnInfo(name = "umami") val umami: Double?,
    @ColumnInfo(name = "sweetness") val sweetness: Double?,
    @ColumnInfo(name = "bitterness") val bitterness: Double?,
    @ColumnInfo(name = "astringency") val astringency: Double?,
    @ColumnInfo(name = "body") val body: Double?,
    @ColumnInfo(name = "aroma") val aroma: Double?,
)

/** How often [descriptorKey] was ticked on a tea's sessions (TEA-13). */
data class DescriptorCountRow(
    @ColumnInfo(name = "descriptor_key") val descriptorKey: String,
    @ColumnInfo(name = "count") val count: Int,
)

/**
 * The tasting writes and the per-tea tasting reads (05 §3.6, §3.8, JRN-02–JRN-05, D-112). Every write acts on a
 * `COMPLETED` session only and returns the number of session rows it changed (0: no such completed session); none
 * touches steps, stock, cost or `active_brew`. The reads count the sessions that are no longer in progress.
 */
@Suppress("TooManyFunctions") // one statement per tasting input and one read per summary (05 §3.6, §3.8)
@Dao
interface TastingDao {
    /** Whether [sessionId] is a `COMPLETED` session. */
    @Query("SELECT EXISTS(SELECT 1 FROM brew_session WHERE id = :sessionId AND status = 'COMPLETED')")
    suspend fun isCompleted(sessionId: String): Boolean

    @Query("UPDATE brew_session SET rating = :rating, updated_at = :now WHERE id = :sessionId AND $COMPLETED")
    suspend fun setRating(sessionId: String, rating: Double?, now: Long): Int

    /** Sets the column of [axis] (a `TasteAxis` name) to [value]; the other five stay. */
    @Query(
        "UPDATE brew_session SET " +
            "taste_umami = CASE WHEN :axis = 'UMAMI' THEN :value ELSE taste_umami END, " +
            "taste_sweetness = CASE WHEN :axis = 'SWEETNESS' THEN :value ELSE taste_sweetness END, " +
            "taste_bitterness = CASE WHEN :axis = 'BITTERNESS' THEN :value ELSE taste_bitterness END, " +
            "taste_astringency = CASE WHEN :axis = 'ASTRINGENCY' THEN :value ELSE taste_astringency END, " +
            "taste_body = CASE WHEN :axis = 'BODY' THEN :value ELSE taste_body END, " +
            "taste_aroma = CASE WHEN :axis = 'AROMA' THEN :value ELSE taste_aroma END, " +
            "updated_at = :now WHERE id = :sessionId AND $COMPLETED",
    )
    suspend fun setTaste(sessionId: String, axis: String, value: Int?, now: Long): Int

    /** *Zurücksetzen* (JRN-03): all six taste columns null. */
    @Query(
        "UPDATE brew_session SET taste_umami = NULL, taste_sweetness = NULL, taste_bitterness = NULL, " +
            "taste_astringency = NULL, taste_body = NULL, taste_aroma = NULL, updated_at = :now " +
            "WHERE id = :sessionId AND $COMPLETED",
    )
    suspend fun clearTaste(sessionId: String, now: Long): Int

    @Query("UPDATE brew_session SET notes = :notes, updated_at = :now WHERE id = :sessionId AND $COMPLETED")
    suspend fun setNotes(sessionId: String, notes: String?, now: Long): Int

    /** Sets `updated_at` alone, for a descriptor tick (05 §3.7). */
    @Query("UPDATE brew_session SET updated_at = :now WHERE id = :sessionId AND $COMPLETED")
    suspend fun touch(sessionId: String, now: Long): Int

    @Query("SELECT * FROM session_descriptor WHERE session_id = :sessionId ORDER BY created_at, descriptor_key")
    suspend fun descriptors(sessionId: String): List<SessionDescriptorEntity>

    @Query("SELECT COUNT(*) FROM session_descriptor WHERE session_id = :sessionId")
    suspend fun descriptorCount(sessionId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM session_descriptor WHERE session_id = :sessionId AND descriptor_key = :key)")
    suspend fun hasDescriptor(sessionId: String, key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDescriptor(descriptor: SessionDescriptorEntity)

    @Query("DELETE FROM session_descriptor WHERE session_id = :sessionId AND descriptor_key = :key")
    suspend fun deleteDescriptor(sessionId: String, key: String)

    /** Every tea with a rated session: its average rating and the number of rated sessions (TEA-08, TEA-11). */
    @Query(
        "SELECT tea_id, AVG(rating) AS average, COUNT(rating) AS count FROM brew_session " +
            "WHERE tea_id IS NOT NULL AND rating IS NOT NULL AND $FINISHED GROUP BY tea_id",
    )
    fun observeTeaRatings(): Flow<List<TeaRatingRow>>

    /** [teaId]'s rating as [observeTeaRatings] has it; no row without a rated session. */
    @Query(
        "SELECT tea_id, AVG(rating) AS average, COUNT(rating) AS count FROM brew_session " +
            "WHERE tea_id = :teaId AND rating IS NOT NULL AND $FINISHED GROUP BY tea_id",
    )
    fun observeTeaRating(teaId: String): Flow<TeaRatingRow?>

    /** [teaId]'s taste averages: SQL's AVG skips nulls, so each axis averages the sessions that set it. */
    @Query(
        "SELECT AVG(taste_umami) AS umami, AVG(taste_sweetness) AS sweetness, " +
            "AVG(taste_bitterness) AS bitterness, AVG(taste_astringency) AS astringency, " +
            "AVG(taste_body) AS body, AVG(taste_aroma) AS aroma FROM brew_session " +
            "WHERE tea_id = :teaId AND $FINISHED",
    )
    fun observeTasteAverages(teaId: String): Flow<TasteAveragesRow>

    /** [teaId]'s descriptor keys with how many of its sessions ticked each, most frequent first, then by key. */
    @Query(
        "SELECT session_descriptor.descriptor_key AS descriptor_key, COUNT(*) AS count FROM session_descriptor " +
            "JOIN brew_session ON brew_session.id = session_descriptor.session_id " +
            "WHERE brew_session.tea_id = :teaId AND brew_session.$FINISHED " +
            "GROUP BY session_descriptor.descriptor_key ORDER BY count DESC, descriptor_key",
    )
    fun observeDescriptorCounts(teaId: String): Flow<List<DescriptorCountRow>>
}

private const val COMPLETED = "status = 'COMPLETED'"
private const val FINISHED = "status != 'IN_PROGRESS'"

/** The journal's read side (SCR-15, JRN-08): read-only, the writes are [SessionDao]'s. */
@Dao
interface JournalDao {
    /**
     * Every session, newest first, with its steps and its tea's cover photo. Emits again on
     * every write to the sessions, their steps or the teas.
     */
    @Transaction
    @Query(
        "SELECT brew_session.*, tea.cover_photo_id AS tea_cover_photo_id FROM brew_session " +
            "LEFT JOIN tea ON tea.id = brew_session.tea_id ORDER BY brew_session.started_at DESC",
    )
    fun observeSessions(): Flow<List<JournalSessionRow>>
}

/** The `active_brew` rows (05 §3.12), from schema v4 one per [TimerSlot] (D-098). */
@Dao
interface ActiveBrewDao {
    /** The row of [slot]; null while that slot has no active timer. */
    @Query("SELECT * FROM active_brew WHERE slot = :slot")
    suspend fun get(slot: TimerSlot): ActiveBrewEntity?

    /** Writes the row of its [ActiveBrewEntity.slot]; the other slot's row is untouched. */
    @Upsert
    suspend fun upsert(activeBrew: ActiveBrewEntity)

    /** Deletes the row of [slot] only. */
    @Query("DELETE FROM active_brew WHERE slot = :slot")
    suspend fun delete(slot: TimerSlot)

    /** Deletes the rows of both slots. */
    @Query("DELETE FROM active_brew")
    suspend fun clear()

    /** The tea of the active brew session; null without one, for the quick timer or a session without a tea (E15). */
    @Query(ACTIVE_TEA_ID)
    fun observeActiveTeaId(): Flow<String?>

    /** The teas of the active brew sessions, in either brew slot (E15, D-108); a session without a tea adds none. */
    @Query("$ACTIVE_TEA_ID WHERE brew_session.tea_id IS NOT NULL")
    fun observeActiveTeaIds(): Flow<List<String>>

    /** Whether [teaId] is the tea of an active brew session, in either brew slot (E15, D-108). */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM active_brew JOIN brew_session ON brew_session.id = active_brew.session_id " +
            "WHERE brew_session.tea_id = :teaId)",
    )
    suspend fun isActiveTea(teaId: String): Boolean

    /** The vessels of the active brew sessions, in either brew slot (WARE-01, D-108). */
    @Query("$ACTIVE_VESSEL_ID WHERE brew_session.vessel_id IS NOT NULL")
    fun observeActiveVesselIds(): Flow<List<String>>

    /** Whether [vesselId] is the vessel of an active brew session, in either brew slot (WARE-01, D-108). */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM active_brew JOIN brew_session ON brew_session.id = active_brew.session_id " +
            "WHERE brew_session.vessel_id = :vesselId)",
    )
    suspend fun isActiveVessel(vesselId: String): Boolean
}

private const val ACTIVE_TEA_ID = "SELECT brew_session.tea_id FROM active_brew " +
    "JOIN brew_session ON brew_session.id = active_brew.session_id"

private const val ACTIVE_VESSEL_ID = "SELECT brew_session.vessel_id FROM active_brew " +
    "JOIN brew_session ON brew_session.id = active_brew.session_id"
