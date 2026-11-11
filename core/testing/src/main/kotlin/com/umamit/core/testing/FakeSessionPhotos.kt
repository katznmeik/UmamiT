package com.umamit.core.testing

import com.umamit.core.data.AddPhotoResult
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionPhotos
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * [SessionPhotos] on [sessions], as on Room (JRN-07): an added photo goes after the session's others, at most
 * [Photo.MAX_PER_SESSION], only to a `COMPLETED` session; a deleted session's photos are gone with it. The image
 * isn't decoded: a URI in [unreadable] is refused as unreadable, any other is a 2048 × 1536 photo. With [files] each
 * added photo also gets a (placeholder) file, so the screens find its path, and a deleted one loses it.
 */
class FakeSessionPhotos(private val sessions: FakeSessions, private val files: PhotoFiles? = null) : SessionPhotos {
    /** The URIs that are no readable image. */
    val unreadable = mutableSetOf<String>()

    /** Every URI [add] was called with, in order. */
    val added = mutableListOf<String>()

    /** When true, [delete] fails as a refused write does. */
    var failDeletes = false

    private val rows = MutableStateFlow<List<Photo>>(emptyList())
    private var nextId = 1

    override fun observePhotos(sessionId: String): Flow<List<Photo>> =
        combine(rows, sessions.sessionRows) { photos, sessionRows ->
            if (sessionId in sessionRows) photos.filter { it.owner == PhotoOwner.OfSession(sessionId) } else emptyList()
        }

    override suspend fun add(sessionId: String, uri: String): AddPhotoResult {
        added += uri
        val session = sessions.sessionRows.value[sessionId]
        val own = rows.value.filter { it.owner == PhotoOwner.OfSession(sessionId) }
        return when {
            session?.status != SessionStatus.COMPLETED -> AddPhotoResult.Failed

            own.size >= Photo.MAX_PER_SESSION -> AddPhotoResult.LimitReached

            uri in unreadable -> AddPhotoResult.Unreadable

            else -> {
                val id = "session-photo-${nextId++}"
                files?.write(id, PLACEHOLDER_BYTES)
                rows.update {
                    it + Photo(
                        id = id,
                        owner = PhotoOwner.OfSession(sessionId),
                        origin = PhotoOrigin.OWN,
                        widthPx = WIDTH,
                        heightPx = HEIGHT,
                        byteSize = PLACEHOLDER_BYTES.size.toLong(),
                        position = (own.maxOfOrNull { photo -> photo.position } ?: -1) + 1,
                    )
                }
                AddPhotoResult.Added(id)
            }
        }
    }

    override suspend fun delete(sessionId: String, photoId: String): Boolean {
        val photo = rows.value.firstOrNull { it.id == photoId && it.owner == PhotoOwner.OfSession(sessionId) }
        if (photo == null || failDeletes) return false
        rows.update { it - photo }
        files?.delete(photoId)
        return true
    }

    private companion object {
        const val WIDTH = 2048
        const val HEIGHT = 1536
        val PLACEHOLDER_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    }
}
