package com.umamit.core.testing

import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SetVesselPhotoResult
import com.umamit.core.data.VesselPhotos
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import kotlinx.coroutines.flow.update

/**
 * [VesselPhotos] on [collection], as on Room: a set photo replaces the vessel's former one and becomes its
 * cover. The image isn't decoded: a URI in [unreadable] is refused as unreadable, any other is a 2048 × 1536
 * photo. With [files] each set photo also gets a (placeholder) file and a replaced or removed one loses its
 * file, so the screens find the path.
 */
class FakeVesselPhotos(private val collection: FakeCollection, private val files: PhotoFiles? = null) :
    VesselPhotos {
    /** The URIs that are no readable image. */
    val unreadable = mutableSetOf<String>()

    /** Every URI [set] was called with, in order. */
    val set = mutableListOf<String>()

    /** How often [remove] was called. */
    var removed = 0
        private set

    private var nextId = 1

    override suspend fun set(vesselId: String, uri: String): SetVesselPhotoResult {
        set += uri
        val vessel = collection.vesselRows.value[vesselId]
        return when {
            vessel == null -> SetVesselPhotoResult.Failed

            uri in unreadable -> SetVesselPhotoResult.Unreadable

            else -> {
                val id = "vessel-photo-${nextId++}"
                files?.write(id, PLACEHOLDER_BYTES)
                dropPhotosOf(vesselId)
                val photo = Photo(
                    id = id,
                    owner = PhotoOwner.OfVessel(vesselId),
                    origin = PhotoOrigin.OWN,
                    widthPx = WIDTH,
                    heightPx = HEIGHT,
                    byteSize = PLACEHOLDER_BYTES.size.toLong(),
                )
                collection.photoRows.update { it + (id to photo) }
                collection.vesselRows.update { it + (vesselId to vessel.copy(coverPhotoId = id)) }
                SetVesselPhotoResult.Set(id)
            }
        }
    }

    override suspend fun remove(vesselId: String): Boolean {
        removed++
        dropPhotosOf(vesselId)
        collection.vesselRows.update { map ->
            map[vesselId]?.let { map + (vesselId to it.copy(coverPhotoId = null)) } ?: map
        }
        return true
    }

    private fun dropPhotosOf(vesselId: String) {
        val own = collection.photoRows.value.values.filter { it.owner == PhotoOwner.OfVessel(vesselId) }
        own.forEach { files?.delete(it.id) }
        collection.photoRows.update { map -> map - own.map { it.id }.toSet() }
    }

    private companion object {
        const val WIDTH = 2048
        const val HEIGHT = 1536
        val PLACEHOLDER_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    }
}
