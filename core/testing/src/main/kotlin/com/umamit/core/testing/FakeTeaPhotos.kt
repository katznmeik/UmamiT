package com.umamit.core.testing

import com.umamit.core.data.AddPhotoResult
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.TeaPhotos
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * [TeaPhotos] on [collection], as on Room: an added photo goes after the tea's others, the first becomes the
 * cover, at most [Photo.MAX_PER_TEA]. The image isn't decoded: a URI in [unreadable] is refused as unreadable,
 * any other is a 2048 × 1536 photo. With [files] each added photo also gets a (placeholder) file, so the screens
 * find its path.
 */
class FakeTeaPhotos(private val collection: FakeCollection, private val files: PhotoFiles? = null) : TeaPhotos {
    /** The URIs that are no readable image. */
    val unreadable = mutableSetOf<String>()

    /** Every URI [add] was called with, in order. */
    val added = mutableListOf<String>()

    private var nextId = 1

    override fun observePhotos(teaId: String): Flow<List<Photo>> = collection.photoRows.map { rows ->
        rows.values.filter { it.owner == PhotoOwner.OfTea(teaId) }.sortedBy { it.position }
    }

    override suspend fun add(teaId: String, uri: String): AddPhotoResult {
        added += uri
        val tea = collection.teaRows.value[teaId]
        val own = collection.photoRows.value.values.filter { it.owner == PhotoOwner.OfTea(teaId) }
        return when {
            tea == null -> AddPhotoResult.Failed

            own.size >= Photo.MAX_PER_TEA -> AddPhotoResult.LimitReached

            uri in unreadable -> AddPhotoResult.Unreadable

            else -> {
                val id = "photo-${nextId++}"
                files?.write(id, PLACEHOLDER_BYTES)
                val photo = Photo(
                    id = id,
                    owner = PhotoOwner.OfTea(teaId),
                    origin = PhotoOrigin.OWN,
                    widthPx = WIDTH,
                    heightPx = HEIGHT,
                    byteSize = PLACEHOLDER_BYTES.size.toLong(),
                    position = (own.maxOfOrNull { it.position } ?: -1) + 1,
                )
                collection.photoRows.update { it + (id to photo) }
                if (tea.coverPhotoId == null) collection.teas.setCoverPhoto(teaId, id)
                AddPhotoResult.Added(id)
            }
        }
    }

    private companion object {
        const val WIDTH = 2048
        const val HEIGHT = 1536
        val PLACEHOLDER_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    }
}
