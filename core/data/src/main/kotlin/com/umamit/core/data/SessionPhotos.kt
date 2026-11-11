package com.umamit.core.data

import android.content.Context
import android.database.SQLException
import androidx.room.withTransaction
import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * A finished session's own photos (JRN-07, SCR-16, 05 §3.9 „Session photo writes", D-112): up to
 * [Photo.MAX_PER_SESSION], in the order added, without a cover. Deleting the session (JRN-10) takes them along
 * ([JournalRepository.deleteSession]).
 */
interface SessionPhotos {
    /** The session's photos in the order added (`position`, then `created_at`). */
    fun observePhotos(sessionId: String): Flow<List<Photo>>

    /**
     * Adds the image the photo picker returned as [uri] (a content URI) to session [sessionId] after its others,
     * processed like a tea photo (06 §5.2). Refused for a session that isn't `COMPLETED` and at
     * [Photo.MAX_PER_SESSION] photos, checked before the image is read and again in the row's transaction. The file
     * is written before the row and removed again when the row isn't.
     */
    suspend fun add(sessionId: String, uri: String): AddPhotoResult

    /**
     * *Löschen*: removes photo [photoId] of session [sessionId]: the row in a transaction, the file after the commit.
     * False when the photo isn't the session's or the write failed; then the file stays.
     */
    suspend fun delete(sessionId: String, photoId: String): Boolean
}

/**
 * [SessionPhotos] on Room and [PhotoFiles]. [load] reads and processes a picked image (06 §5.2), null when it can't
 * be read or decoded; the app reads it through its content resolver.
 */
class RoomSessionPhotos internal constructor(
    private val db: UmamiDatabase,
    private val time: WallClock,
    private val files: PhotoFiles,
    private val load: suspend (uri: String) -> EncodedPhoto?,
) : SessionPhotos {
    @Inject
    constructor(
        db: UmamiDatabase,
        time: WallClock,
        files: PhotoFiles,
        @ApplicationContext context: Context,
    ) : this(db, time, files, { uri -> loadPicked(context, uri) })

    override fun observePhotos(sessionId: String): Flow<List<Photo>> =
        db.sessionPhotoDao().observePhotos(sessionId).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(sessionId: String, uri: String): AddPhotoResult = try {
        // NonCancellable: leaving the page while the photo is processed never leaves a file without its row.
        withContext(NonCancellable + Dispatchers.IO) { addNow(sessionId, uri) }
    } catch (_: SQLException) {
        AddPhotoResult.Failed
    } catch (_: IOException) {
        AddPhotoResult.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Rule 8: the timer runs in this process; a bug in the processing fails the add, never the app (D-090).
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: RuntimeException,
    ) {
        AddPhotoResult.Failed
    }

    private suspend fun addNow(sessionId: String, uri: String): AddPhotoResult {
        // Refused before the image is read when the answer is known already.
        val photo = if (refusal(sessionId) == null) load(uri) else null
        return when {
            photo != null -> store(sessionId, photo)
            else -> refusal(sessionId) ?: AddPhotoResult.Unreadable
        }
    }

    /** Writes [photo]'s file, then its row; a row that isn't written takes the file with it. */
    private suspend fun store(sessionId: String, photo: EncodedPhoto): AddPhotoResult {
        val id = UUID.randomUUID().toString()
        files.write(id, photo.bytes)
        var result: AddPhotoResult = AddPhotoResult.Failed
        try {
            result = db.withTransaction {
                refusal(sessionId) ?: insert(sessionId, id, photo)
            }
        } finally {
            if (result !is AddPhotoResult.Added) files.delete(id)
        }
        return result
    }

    /** Why session [sessionId] takes no photo now, or null when it takes one. */
    private suspend fun refusal(sessionId: String): AddPhotoResult? = when {
        !db.tastingDao().isCompleted(sessionId) -> AddPhotoResult.Failed
        db.sessionPhotoDao().count(sessionId) >= Photo.MAX_PER_SESSION -> AddPhotoResult.LimitReached
        else -> null
    }

    private suspend fun insert(sessionId: String, id: String, photo: EncodedPhoto): AddPhotoResult {
        val dao = db.sessionPhotoDao()
        val now = time.nowMs()
        val row = Photo(
            id = id,
            owner = PhotoOwner.OfSession(sessionId),
            origin = PhotoOrigin.OWN,
            widthPx = photo.width,
            heightPx = photo.height,
            byteSize = photo.bytes.size.toLong(),
            position = dao.maxPosition(sessionId)?.plus(1) ?: 0,
        )
        dao.insert(row.toEntity(createdAt = now, updatedAt = now))
        return AddPhotoResult.Added(id)
    }

    override suspend fun delete(sessionId: String, photoId: String): Boolean = try {
        withContext(NonCancellable + Dispatchers.IO) {
            val deleted = db.withTransaction { db.sessionPhotoDao().delete(sessionId, photoId) > 0 }
            // After the commit: a failed transaction keeps the file its row still names.
            if (deleted) files.delete(photoId)
            deleted
        }
    } catch (_: SQLException) {
        false
    }
}

/** Binds [SessionPhotos] (JRN-07). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionPhotosModule {
    @Binds
    abstract fun sessionPhotos(impl: RoomSessionPhotos): SessionPhotos
}
