package com.umamit.core.data.catalog

import androidx.room.withTransaction
import com.umamit.core.data.EncodedPhoto
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.PhotoTranscoder
import com.umamit.core.data.SeedData
import com.umamit.core.data.TeaRepository
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.database.dao.CatalogDao
import com.umamit.core.database.entity.CatalogTeaEntity
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.model.Tea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * CAT-15: links the seed teas Tanabata and Hisui to their catalog entries after a successful
 * refresh, once. The catalog refresh calls it after each successful swap; only the first call
 * that finds a seed tea does anything.
 */
fun interface CatalogSeedLinker {
    /** Links the seed teas unless they were linked before; returns the catalog keys it linked. */
    suspend fun linkSeedTeas(): List<String>
}

/** CAT-15's marker in the settings file: set once the seed teas are linked, never reset. */
interface SeedTeaLinkMarker {
    val linked: Flow<Boolean>

    suspend fun markLinked()
}

/** [SeedTeaLinkMarker] in the settings' DataStore, next to the seed markers (05 §6). */
class DataStoreSeedTeaLinkMarker @Inject constructor(private val source: SettingsDataSource) : SeedTeaLinkMarker {
    override val linked: Flow<Boolean> = source.seedTeasLinked

    override suspend fun markLinked() = source.markSeedTeasLinked()
}

/**
 * [CatalogSeedLinker] from `catalog.db` and its photos into `umamit.db` and `filesDir/photos/`
 * (two databases without a shared transaction, D-076).
 *
 * - **Which teas:** a seed tea whose `catalog_key` the catalog holds and which has no description
 *   (CAT-15). A seed tea with a description of its own (a restore, the PO's edit) is left alone.
 * - **What it gets:** the catalog's description (its plain text, as *In Sammlung aufnehmen*
 *   copies it, at most 05 §3.1's 10 000 characters) and, without a cover, the catalog photo
 *   re-encoded as the collection's JPEG (D-076). Recipes, stock, journal and every other field
 *   stay as they are (CAT-13).
 * - **Once:** the [SeedTeaLinkMarker] is set after the first call that found a seed tea, so a
 *   description the PO empties later is never filled in again. Each tea is written in one
 *   transaction that checks again that it has no description and no cover, so a run cut short
 *   before the marker (a process death) changes nothing when it runs again. Before the seed data
 *   exists (a seed retried at the next app start, D-075) nothing is marked, so the first refresh
 *   after the seed links it.
 * - **Failures** (storage) throw; the refresh logs them and the next successful refresh tries
 *   again, as the marker isn't set.
 */
class RoomCatalogSeedLinker @Inject constructor(
    private val db: UmamiDatabase,
    private val teas: TeaRepository,
    private val catalog: CatalogDao,
    private val catalogPhotos: CatalogPhotoFiles,
    private val photoFiles: PhotoFiles,
    private val marker: SeedTeaLinkMarker,
) : CatalogSeedLinker {
    override suspend fun linkSeedTeas(): List<String> {
        val seedTeas = if (marker.linked.first()) emptyList() else SEED_TEA_IDS.mapNotNull { teas.getTea(it) }
        // Linked before, the seed still to come (D-075) or both seed teas deleted: nothing to mark.
        if (seedTeas.isEmpty()) return emptyList()
        val linked = seedTeas.filter { it.description.isNullOrBlank() }.mapNotNull { link(it) }
        marker.markLinked()
        return linked
    }

    /** Links [tea], which has no description, when the catalog holds its key; returns the key when it wrote. */
    private suspend fun link(tea: Tea): String? {
        val entry = tea.catalogKey?.let { catalog.tea(it) } ?: return null
        val description = entry.descriptionText?.takeIf { it.isNotBlank() }?.take(DESCRIPTION_MAX)
        val cover = if (tea.coverPhotoId == null) cover(entry) else null
        // Once the photo is re-encoded, file and rows are written without interruption.
        val written = (description != null || cover != null) &&
            withContext(NonCancellable + Dispatchers.IO) { write(tea.id, description, cover) }
        return entry.key.takeIf { written }
    }

    /** The catalog photo as the collection's JPEG (D-076), or null without a usable photo file. */
    private suspend fun cover(entry: CatalogTeaEntity): EncodedPhoto? {
        val bytes = entry.photoFile?.let { withContext(Dispatchers.IO) { readPhoto(entry.key) } }
        return bytes?.let { withContext(Dispatchers.Default) { PhotoTranscoder.transcodeCatalogCopy(it) } }
    }

    private fun readPhoto(key: String): ByteArray? = try {
        catalogPhotos.photo(key).takeIf { it.isFile }?.readBytes()
    } catch (_: IOException) {
        null
    }

    /** Writes [description] and [cover] to tea [teaId] where it still has none; false when nothing was written. */
    private suspend fun write(teaId: String, description: String?, cover: EncodedPhoto?): Boolean {
        val file = cover?.let { CoverFile(UUID.randomUUID().toString(), it) }
        file?.let { photoFiles.write(it.id, it.photo.bytes) }
        var written = Written.NOTHING
        try {
            written = db.withTransaction { writeRows(teaId, description, file) }
        } finally {
            // Only a committed cover keeps its file.
            if (file != null && written != Written.WITH_COVER) photoFiles.delete(file.id)
        }
        return written != Written.NOTHING
    }

    /** In the transaction: the tea checked again, then its description and its cover where it has none. */
    private suspend fun writeRows(teaId: String, description: String?, file: CoverFile?): Written {
        val tea = teas.getTea(teaId)?.takeIf { it.description.isNullOrBlank() } ?: return Written.NOTHING
        if (description != null) teas.saveTea(tea.copy(description = description))
        val withCover = file != null && tea.coverPhotoId == null
        if (file != null && withCover) {
            teas.savePhoto(
                Photo(
                    id = file.id,
                    owner = PhotoOwner.OfTea(teaId),
                    origin = PhotoOrigin.CATALOG_COPY,
                    widthPx = file.photo.width,
                    heightPx = file.photo.height,
                    byteSize = file.photo.bytes.size.toLong(),
                ),
            )
            teas.setCoverPhoto(teaId, file.id)
        }
        return when {
            withCover -> Written.WITH_COVER
            description != null -> Written.DESCRIPTION
            else -> Written.NOTHING
        }
    }

    /** A cover's re-encoded photo and the id of its file `photos/<id>.jpg`. */
    private class CoverFile(val id: String, val photo: EncodedPhoto)

    private enum class Written { NOTHING, DESCRIPTION, WITH_COVER }

    private companion object {
        val SEED_TEA_IDS = listOf(SeedData.TANABATA_TEA_ID, SeedData.HISUI_TEA_ID)

        /** 05 §3.1's limit of `tea.description`. */
        const val DESCRIPTION_MAX = 10_000
    }
}
