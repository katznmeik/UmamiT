package com.umamit.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.SessionStepEntity
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.flow.Flow

/** A session row with its step rows (in any order; sort by `position`) and its tea's cover photo id. */
data class JournalSessionRow(
    @Embedded val session: BrewSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id")
    val steps: List<SessionStepEntity>,
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

    @Query("SELECT * FROM session_step WHERE session_id = :sessionId ORDER BY position")
    suspend fun steps(sessionId: String): List<SessionStepEntity>

    @Insert
    suspend fun insertSteps(steps: List<SessionStepEntity>)

    @Update
    suspend fun updateSteps(steps: List<SessionStepEntity>)

    /** Deletes the rows of [sessionId] from [position] on: the pending suffix (05 §3.7). */
    @Query("DELETE FROM session_step WHERE session_id = :sessionId AND position >= :position")
    suspend fun deleteStepsFrom(sessionId: String, position: Int)

    /** Deletes the rows that were never started, i.e. carry no recorded values (05 §3.7, TMR-12). */
    @Query("DELETE FROM session_step WHERE session_id = :sessionId AND actual_seconds IS NULL")
    suspend fun deleteUnrecordedSteps(sessionId: String)
}

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

    /**
     * The one active timer while only one may run (until v0.3.0-03): the brew slot's row, else the quick slot's;
     * null for none.
     */
    @Query("SELECT * FROM active_brew ORDER BY CASE slot WHEN 'BREW' THEN 0 ELSE 1 END LIMIT 1")
    suspend fun get(): ActiveBrewEntity?

    /** Writes the row of its [ActiveBrewEntity.slot]; the other slot's row is untouched. */
    @Upsert
    suspend fun upsert(activeBrew: ActiveBrewEntity)

    /** Deletes the row of [slot] only. */
    @Query("DELETE FROM active_brew WHERE slot = :slot")
    suspend fun delete(slot: TimerSlot)

    /** Deletes every row but [slot]'s. */
    @Query("DELETE FROM active_brew WHERE slot != :slot")
    suspend fun deleteOtherThan(slot: TimerSlot)

    /** Deletes the rows of both slots. */
    @Query("DELETE FROM active_brew")
    suspend fun clear()

    /** The tea of the active brew session; null without one, for the quick timer or a session without a tea (E15). */
    @Query(ACTIVE_TEA_ID)
    fun observeActiveTeaId(): Flow<String?>

    /** [observeActiveTeaId], read once. */
    @Query(ACTIVE_TEA_ID)
    suspend fun activeTeaId(): String?
}

private const val ACTIVE_TEA_ID = "SELECT brew_session.tea_id FROM active_brew " +
    "JOIN brew_session ON brew_session.id = active_brew.session_id"
