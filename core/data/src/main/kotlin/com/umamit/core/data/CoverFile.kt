package com.umamit.core.data

import com.umamit.core.data.catalog.CatalogPhotoFiles
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

// A shop photo saved as a collection tea's cover (D-076): the seed photos (ONB-06), the seed teas' link (CAT-15)
// and the add from the catalog (CAT-12) store it the same way.

/** A cover's re-encoded photo and the id of its file `photos/<id>.jpg` (a new one by default). */
internal class CoverFile(val photo: EncodedPhoto, val id: String = UUID.randomUUID().toString())

/**
 * [entry]'s catalog photo re-encoded as the collection's JPEG (D-076), or null without a usable photo file (none,
 * gone or unreadable).
 */
internal suspend fun CatalogPhotoFiles.coverCopy(entry: CatalogTeaEntity): EncodedPhoto? {
    val bytes = entry.photoFile?.let {
        withContext(Dispatchers.IO) {
            try {
                photo(entry.key).takeIf { it.isFile }?.readBytes()
            } catch (_: IOException) {
                null
            }
        }
    }
    return bytes?.let { withContext(Dispatchers.Default) { PhotoTranscoder.transcodeCatalogCopy(it) } }
}

/**
 * Writes [cover]'s file (when there is one), then runs [rows]; the file is deleted again unless [rows] returns a
 * result that [kept] says stored the cover's row.
 */
internal suspend fun <T> PhotoFiles.withCoverFile(cover: CoverFile?, kept: (T) -> Boolean, rows: suspend () -> T): T {
    cover?.let { write(it.id, it.photo.bytes) }
    var stored = false
    try {
        return rows().also { stored = kept(it) }
    } finally {
        if (cover != null && !stored) delete(cover.id)
    }
}

/** In the caller's transaction: [cover]'s `CATALOG_COPY` row for tea [teaId], made its cover. */
internal suspend fun TeaRepository.saveCover(teaId: String, cover: CoverFile) {
    savePhoto(cover.photo.toPhoto(cover.id, PhotoOwner.OfTea(teaId), PhotoOrigin.CATALOG_COPY))
    setCoverPhoto(teaId, cover.id)
}
