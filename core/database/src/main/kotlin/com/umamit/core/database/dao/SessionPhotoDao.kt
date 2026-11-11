package com.umamit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.umamit.core.database.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Row access to a session's photos in `photo` (05 §3.9 „Session photo writes", JRN-07): the rows whose `session_id`
 * is set. The writes and their transactions are in core:data (`RoomSessionPhotos`).
 */
@Dao
interface SessionPhotoDao {
    /** The session's photos in the order added: by `position`, then by `created_at` (05 §3.9). */
    @Query("SELECT * FROM photo WHERE session_id = :sessionId ORDER BY position, created_at, id")
    fun observePhotos(sessionId: String): Flow<List<PhotoEntity>>

    /** How many photos the session has (JRN-07: at most 6). */
    @Query("SELECT COUNT(*) FROM photo WHERE session_id = :sessionId")
    suspend fun count(sessionId: String): Int

    /** The session's highest photo `position`, or null without photos. */
    @Query("SELECT MAX(position) FROM photo WHERE session_id = :sessionId")
    suspend fun maxPosition(sessionId: String): Int?

    /** The ids of the session's photos, whose files go when the session is deleted (JRN-10). */
    @Query("SELECT id FROM photo WHERE session_id = :sessionId")
    suspend fun photoIds(sessionId: String): List<String>

    @Insert
    suspend fun insert(photo: PhotoEntity)

    /** Deletes photo [id] when it is session [sessionId]'s; the number of rows deleted (0 or 1). */
    @Query("DELETE FROM photo WHERE id = :id AND session_id = :sessionId")
    suspend fun delete(sessionId: String, id: String): Int
}
