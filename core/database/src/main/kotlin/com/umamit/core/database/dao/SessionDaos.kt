package com.umamit.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.umamit.core.database.entity.ActiveBrewEntity
import com.umamit.core.database.entity.BrewSessionEntity
import com.umamit.core.database.entity.SessionStepEntity
import kotlinx.coroutines.flow.Flow

/** Row access to `brew_session` and `session_step` (05 §3.6, §3.7); the lifecycle comes with #36. */
@Dao
interface SessionDao {
    @Query("SELECT * FROM brew_session WHERE id = :id")
    suspend fun get(id: String): BrewSessionEntity?

    @Query("SELECT * FROM brew_session WHERE status != 'IN_PROGRESS' ORDER BY started_at DESC")
    fun observeFinished(): Flow<List<BrewSessionEntity>>

    @Upsert
    suspend fun upsert(session: BrewSessionEntity)

    @Query("DELETE FROM brew_session WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM session_step WHERE session_id = :sessionId ORDER BY position")
    suspend fun steps(sessionId: String): List<SessionStepEntity>

    @Query("SELECT * FROM session_step WHERE session_id = :sessionId ORDER BY position")
    fun observeSteps(sessionId: String): Flow<List<SessionStepEntity>>

    @Upsert
    suspend fun upsertSteps(steps: List<SessionStepEntity>)

    @Query("DELETE FROM session_step WHERE id IN (:ids)")
    suspend fun deleteSteps(ids: List<String>)
}

/** The single `active_brew` row (05 §3.12). */
@Dao
interface ActiveBrewDao {
    @Query("SELECT * FROM active_brew WHERE id = ${ActiveBrewEntity.SINGLE_ROW_ID}")
    suspend fun get(): ActiveBrewEntity?

    @Query("SELECT * FROM active_brew WHERE id = ${ActiveBrewEntity.SINGLE_ROW_ID}")
    fun observe(): Flow<ActiveBrewEntity?>

    @Upsert
    suspend fun upsert(activeBrew: ActiveBrewEntity)

    @Query("DELETE FROM active_brew")
    suspend fun clear()
}
