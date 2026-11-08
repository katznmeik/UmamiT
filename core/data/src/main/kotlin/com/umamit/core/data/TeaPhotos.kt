package com.umamit.core.data

import android.content.Context
import android.database.SQLException
import androidx.core.net.toUri
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** How [TeaPhotos.add] ended. */
sealed interface AddPhotoResult {
    /** The photo [photoId] was stored; the tea's first photo is its cover. */
    data class Added(val photoId: String) : AddPhotoResult

    /** The tea already has [Photo.MAX_PER_TEA] photos; nothing was written („Höchstens 10 Fotos"). */
    data object LimitReached : AddPhotoResult

    /** The picked image could not be read or decoded; nothing was written. */
    data object Unreadable : AddPhotoResult

    /** The tea is gone or the write failed; nothing was written. */
    data object Failed : AddPhotoResult
}

/**
 * A tea's photos (TEA-05, SCR-28, 05 §3.9, D-104): the user's own and a catalog tea's shop copy, which is just
 * its first photo.
 */
interface TeaPhotos {
    /** The tea's photos in their order (`position`). */
    fun observePhotos(teaId: String): Flow<List<Photo>>

    /**
     * Adds the image the photo picker returned as [uri] (a content URI) to tea [teaId] as an own photo after the
     * others: decoded with sampling, turned upright by its EXIF orientation (#58), at most 2048 px, JPEG 85, no
     * metadata, stored in `filesDir/photos/` (06 §5.2). The tea's first photo becomes its cover. Refused at
     * [Photo.MAX_PER_TEA] photos. The file is written before the row and removed again when the row isn't.
     */
    suspend fun add(teaId: String, uri: String): AddPhotoResult
}

/** [TeaPhotos] on Room and [PhotoFiles]; a picked image is read through the app's content resolver. */
class RoomTeaPhotos @Inject constructor(
    private val db: UmamiDatabase,
    private val time: WallClock,
    private val files: PhotoFiles,
    @param:ApplicationContext private val context: Context,
) : TeaPhotos {
    override fun observePhotos(teaId: String): Flow<List<Photo>> =
        db.teaDao().observeTeaPhotos(teaId).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(teaId: String, uri: String): AddPhotoResult = try {
        // NonCancellable: leaving the detail while the photo is processed never leaves a file without its row.
        withContext(NonCancellable + Dispatchers.IO) { addNow(teaId, uri) }
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

    private suspend fun addNow(teaId: String, uri: String): AddPhotoResult {
        // Refused before the image is read when the answer is known already.
        val photo = if (refusal(teaId) == null) processed(uri) else null
        return when {
            photo != null -> store(teaId, photo)
            else -> refusal(teaId) ?: AddPhotoResult.Unreadable
        }
    }

    /** The picked image at [uri] processed (06 §5.2), or null when it can't be read or decoded. */
    private suspend fun processed(uri: String): EncodedPhoto? =
        read(uri)?.let { bytes -> withContext(Dispatchers.Default) { PhotoTranscoder.transcodePicked(bytes) } }

    /** Writes [photo]'s file, then its row; a row that isn't written takes the file with it. */
    private suspend fun store(teaId: String, photo: EncodedPhoto): AddPhotoResult {
        val id = UUID.randomUUID().toString()
        files.write(id, photo.bytes)
        var result: AddPhotoResult = AddPhotoResult.Failed
        try {
            result = db.withTransaction {
                refusal(teaId) ?: insert(teaId, id, photo)
            }
        } finally {
            if (result !is AddPhotoResult.Added) files.delete(id)
        }
        return result
    }

    /** Why tea [teaId] takes no photo now, or null when it takes one. */
    private suspend fun refusal(teaId: String): AddPhotoResult? = when {
        db.teaDao().get(teaId) == null -> AddPhotoResult.Failed
        db.teaDao().photoCount(teaId) >= Photo.MAX_PER_TEA -> AddPhotoResult.LimitReached
        else -> null
    }

    private suspend fun insert(teaId: String, id: String, photo: EncodedPhoto): AddPhotoResult {
        val dao = db.teaDao()
        val now = time.nowMs()
        val position = dao.maxPhotoPosition(teaId)?.plus(1) ?: 0
        val row = Photo(
            id = id,
            owner = PhotoOwner.OfTea(teaId),
            origin = PhotoOrigin.OWN,
            widthPx = photo.width,
            heightPx = photo.height,
            byteSize = photo.bytes.size.toLong(),
            position = position,
        )
        dao.upsertPhoto(row.toEntity(createdAt = now, updatedAt = now))
        if (dao.get(teaId)?.coverPhotoId == null) dao.setCoverPhoto(teaId, id, now)
        return AddPhotoResult.Added(id)
    }

    /** The bytes behind [uri], or null when it can't be opened or is larger than [MAX_PICKED_BYTES]. */
    private fun read(uri: String): ByteArray? = try {
        context.contentResolver.openInputStream(uri.toUri())?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_PICKED_BYTES) return null
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        // The picker's grant is gone (the app was restarted meanwhile).
        null
    }

    private companion object {
        /** Above a 200 MP camera JPEG; anything larger is no photo the app takes. */
        const val MAX_PICKED_BYTES = 128L * 1024 * 1024
        const val BUFFER_BYTES = 64 * 1024
    }
}

/** Binds [TeaPhotos] (TEA-05). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TeaPhotosModule {
    @Binds
    abstract fun teaPhotos(impl: RoomTeaPhotos): TeaPhotos
}
