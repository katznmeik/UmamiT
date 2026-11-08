package com.umamit.core.data

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.SQLException
import android.net.Uri
import android.util.Log
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
import java.io.InputStream
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

    /**
     * *Als Titelbild*: photo [photoId] of tea [teaId] becomes its cover; the order stays. False when the photo isn't
     * the tea's or the write failed.
     */
    suspend fun setCover(teaId: String, photoId: String): Boolean

    /**
     * *Löschen*: removes photo [photoId] of tea [teaId]. In one transaction the row goes and, when it was the cover,
     * the next photo by `position` (else the first) becomes the cover, none left → no cover; the file goes after the
     * commit. False when the photo isn't the tea's or the write failed; then the file stays.
     */
    suspend fun delete(teaId: String, photoId: String): Boolean
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
    private suspend fun processed(uri: String): EncodedPhoto? = loadPicked(context, uri)

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

    override suspend fun setCover(teaId: String, photoId: String): Boolean = written {
        db.withTransaction {
            val dao = db.teaDao()
            val isTeas = dao.photo(photoId)?.teaId == teaId
            if (isTeas) dao.setCoverPhoto(teaId, photoId, time.nowMs())
            isTeas
        }
    }

    override suspend fun delete(teaId: String, photoId: String): Boolean = written {
        val deleted = db.withTransaction {
            val dao = db.teaDao()
            val photos = dao.teaPhotos(teaId)
            val index = photos.indexOfFirst { it.id == photoId }
            if (index >= 0) {
                val wasCover = dao.get(teaId)?.coverPhotoId == photoId
                dao.deletePhoto(photoId)
                if (wasCover) {
                    val rest = photos - photos[index]
                    // The next by position, else the first; none left: no cover, the style's leaf shows.
                    val next = rest.getOrNull(index) ?: rest.firstOrNull()
                    dao.setCoverPhoto(teaId, next?.id, time.nowMs())
                }
            }
            index >= 0
        }
        // After the commit: a failed transaction keeps the file its row still names.
        if (deleted) files.delete(photoId)
        deleted
    }

    /** [write] off the main thread and not cancelled halfway; false when the database refused it. */
    private suspend fun written(write: suspend () -> Boolean): Boolean = try {
        withContext(NonCancellable + Dispatchers.IO) { write() }
    } catch (_: SQLException) {
        false
    }
}

/**
 * The picked image at [uri] read through the content resolver and processed (06 §5.2), or null. Running out of
 * memory on a huge image refuses that image like an unreadable one: the timer runs in this process (rule 8, D-090).
 */
internal suspend fun loadPicked(context: Context, uri: String): EncodedPhoto? = try {
    readPicked(context, uri)?.let { bytes ->
        withContext(Dispatchers.Default) { PhotoTranscoder.transcodePicked(bytes) }
    }
} catch (e: OutOfMemoryError) {
    Log.w(TAG, "Picked image too large for the memory left", e)
    null
}

/** The bytes behind [uri], or null when it can't be opened or is larger than [MAX_PICKED_BYTES]. */
private fun readPicked(context: Context, uri: String): ByteArray? = try {
    val resolver = context.contentResolver
    val parsed = uri.toUri()
    val declared = declaredLength(resolver, parsed)
    if (declared > MAX_PICKED_BYTES) {
        null
    } else {
        resolver.openInputStream(parsed)?.use { input ->
            if (declared > 0) readSized(input, declared.toInt()) else readRest(input, ByteArrayOutputStream())
        }
    }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    // The picker's grant is gone (the app was restarted meanwhile).
    null
}

/** The length the provider declares for [uri], or [AssetFileDescriptor.UNKNOWN_LENGTH]. */
private fun declaredLength(resolver: ContentResolver, uri: Uri): Long = try {
    resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: AssetFileDescriptor.UNKNOWN_LENGTH
} catch (_: IOException) {
    // The stream is opened next and fails on its own if the file is really gone.
    AssetFileDescriptor.UNKNOWN_LENGTH
}

/**
 * [input] read into an array of its declared [size]: the only copy of the file in memory. A shorter stream is
 * trimmed; a longer one is read on within [MAX_PICKED_BYTES].
 */
private fun readSized(input: InputStream, size: Int): ByteArray? {
    val bytes = ByteArray(size)
    var filled = 0
    var count = 0
    while (filled < size && count >= 0) {
        count = input.read(bytes, filled, size - filled)
        if (count > 0) filled += count
    }
    val next = if (filled < size) -1 else input.read()
    return when {
        filled < size -> bytes.copyOf(filled)

        next < 0 -> bytes

        else -> {
            val out = ByteArrayOutputStream(size + BUFFER_BYTES)
            out.write(bytes, 0, size)
            out.write(next)
            readRest(input, out)
        }
    }
}

/** [out] followed by the rest of [input], or null when the whole is larger than [MAX_PICKED_BYTES]. */
private fun readRest(input: InputStream, out: ByteArrayOutputStream): ByteArray? {
    val buffer = ByteArray(BUFFER_BYTES)
    var count = input.read(buffer)
    while (count >= 0 && out.size().toLong() + count <= MAX_PICKED_BYTES) {
        out.write(buffer, 0, count)
        count = input.read(buffer)
    }
    return if (count < 0) out.toByteArray() else null
}

/** A 200 MP camera JPEG fits; larger files are refused before they are buffered (D-106). */
private const val MAX_PICKED_BYTES = 40L * 1024 * 1024
private const val BUFFER_BYTES = 64 * 1024
private const val TAG = "TeaPhotos"

/** Binds [TeaPhotos] (TEA-05). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TeaPhotosModule {
    @Binds
    abstract fun teaPhotos(impl: RoomTeaPhotos): TeaPhotos
}
