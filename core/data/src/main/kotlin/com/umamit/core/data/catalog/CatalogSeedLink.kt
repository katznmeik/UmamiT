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
 * CAT-15: links the seed teas Tanabata and Hisui to their catalog entries. The catalog refresh
 * calls it after each successful swap; each part of each seed tea is done once (D-090).
 */
fun interface CatalogSeedLinker {
    /**
     * Links what is still to do of the seed teas; [withPhotos] tells whether the refresh downloaded
     * photos (SET-22). Returns the catalog keys of the seed teas it wrote to.
     */
    suspend fun linkSeedTeas(withPhotos: Boolean): List<String>
}

/**
 * CAT-15's state in the settings file (D-090): the done parts, `<tea id>/description` and
 * `<tea id>/cover`; a part is added once and never removed.
 */
interface SeedTeaLinkMarker {
    val done: Flow<Set<String>>

    suspend fun markDone(parts: Set<String>)
}

/** [SeedTeaLinkMarker] in the settings' DataStore, next to the seed markers (05 §6). */
class DataStoreSeedTeaLinkMarker @Inject constructor(private val source: SettingsDataSource) : SeedTeaLinkMarker {
    override val done: Flow<Set<String>> = source.seedTeaLinksDone

    override suspend fun markDone(parts: Set<String>) = source.markSeedTeaLinksDone(parts)
}

/**
 * [CatalogSeedLinker] from `catalog.db` and its photos into `umamit.db` and `filesDir/photos/`
 * (two databases without a shared transaction, D-076).
 *
 * - **Which teas:** the seed teas Tanabata and Hisui (by id, CAT-13) whose `catalog_key` the
 *   catalog holds.
 * - **Two parts per tea** (D-090), each done once:
 *   - *description:* the catalog's plain text (as *In Sammlung aufnehmen* copies it, at most
 *     05 §3.1's 10 000 characters) when the tea has none. Done once the tea has a description,
 *     linked or its own (a restore, the PO's edit, which is never overwritten).
 *   - *cover:* the catalog photo re-encoded as the collection's JPEG (D-076) when the tea has no
 *     cover. Done once the tea has a cover (linked, from the seed photo download or its own), or
 *     when the refresh downloaded no photos (SET-22 off).
 * - **Retried:** a part the refresh couldn't give (the tea's page unread, no description, no
 *   photo, the seed still to come, D-075) is tried again after every later successful refresh; a
 *   done part never again, so a description the PO empties later is never filled in again.
 *   Recipes, stock, journal and every other field stay as they are (CAT-13).
 * - **Idempotent:** each tea is written in one transaction that checks again that it has no
 *   description and no cover, and the parts are marked done after it, so a run cut short before
 *   the marker (a process death) changes nothing when it runs again.
 * - **Failures** (storage) throw; the refresh logs them and the next successful refresh tries
 *   again, as the parts aren't marked.
 */
class RoomCatalogSeedLinker @Inject constructor(
    private val db: UmamiDatabase,
    private val teas: TeaRepository,
    private val catalog: CatalogDao,
    private val catalogPhotos: CatalogPhotoFiles,
    private val photoFiles: PhotoFiles,
    private val marker: SeedTeaLinkMarker,
) : CatalogSeedLinker {
    override suspend fun linkSeedTeas(withPhotos: Boolean): List<String> {
        val done = marker.done.first()
        return SEED_TEA_IDS.mapNotNull { teaId ->
            val todo = Part.entries.filter { it.of(teaId) !in done }.toSet()
            if (todo.isEmpty()) null else linkSeedTea(teaId, todo, withPhotos)
        }
    }

    /** Does [todo] for seed tea [teaId] and marks what is settled now; returns its catalog key when it wrote. */
    private suspend fun linkSeedTea(teaId: String, todo: Set<Part>, withPhotos: Boolean): String? {
        // The seed still to come (D-075) or the tea deleted: nothing to do now.
        val tea = teas.getTea(teaId) ?: return null
        val linked = link(tea, todo)
        val now = teas.getTea(teaId)
        val settled = if (now == null) {
            emptyList()
        } else {
            todo.filter { part ->
                when (part) {
                    Part.DESCRIPTION -> !now.description.isNullOrBlank()
                    Part.COVER -> now.coverPhotoId != null || !withPhotos
                }
            }
        }
        if (settled.isNotEmpty()) marker.markDone(settled.map { it.of(teaId) }.toSet())
        return linked
    }

    /** Gives [tea] what [todo] asks for and the catalog has; returns its catalog key when it wrote. */
    private suspend fun link(tea: Tea, todo: Set<Part>): String? {
        val entry = tea.catalogKey?.let { catalog.tea(it) } ?: return null
        val description = entry.descriptionText?.takeIf { it.isNotBlank() }?.take(DESCRIPTION_MAX)
            ?.takeIf { Part.DESCRIPTION in todo && tea.description.isNullOrBlank() }
        val cover = if (Part.COVER in todo && tea.coverPhotoId == null) cover(entry) else null
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
        val tea = teas.getTea(teaId) ?: return Written.NOTHING
        val withDescription = description != null && tea.description.isNullOrBlank()
        if (withDescription) teas.saveTea(tea.copy(description = description))
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
            withDescription -> Written.DESCRIPTION
            else -> Written.NOTHING
        }
    }

    /** A part of a seed tea's link (D-090), stored as `<tea id>/<name>`. */
    private enum class Part(private val stored: String) {
        DESCRIPTION("description"),
        COVER("cover"),
        ;

        fun of(teaId: String) = "$teaId/$stored"
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
