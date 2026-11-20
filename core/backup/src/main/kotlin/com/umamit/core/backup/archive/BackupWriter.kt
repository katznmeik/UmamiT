package com.umamit.core.backup.archive

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Where a backup write is (BAK-01); the feature turns it into „Fotos 12 von 61". */
sealed interface BackupProgress {
    /** Reading the rows (one read transaction) and the settings. */
    data object Reading : BackupProgress

    /** [done] of [total] photos are in the file. */
    data class Photos(val done: Int, val total: Int) : BackupProgress

    /** Writing `data.json`, `settings.json` and the manifest. */
    data object Finishing : BackupProgress
}

/** What a finished write holds: the manifest it wrote and the bytes it wrote in all. */
data class BackupSummary(val manifest: BackupManifest, val bytes: Long)

/** Everything of the manifest that is not computed from the entries. */
data class BackupHeader(
    val createdAt: Long,
    val zoneId: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val schemaVersion: Int,
    val settingsSchemaVersion: Int,
)

/**
 * Writes a `.umamit` file ([BackupFormat]) to any [OutputStream]: the picker's document (BAK-01) or the safety copy's
 * private file (BAK-02). Pure IO, no Room or DataStore: [photoFile] maps a photo id to its JPEG.
 */
class BackupWriter(private val photoFile: (id: String) -> File) {
    /**
     * Writes [data] and [settings] with the photos to [output] and returns the summary. The photos go first, one by
     * one, with a [BackupProgress.Photos] after each and a cancellation check before each; a photo whose file is
     * missing is left out with its row and the covers that point at it (05 §8). The ZIP is finished but [output] is
     * not closed (the caller owns it). A cancelled or failed write leaves an incomplete file without a manifest,
     * which the caller deletes. Blocking IO: call it off the main thread.
     */
    suspend fun write(
        data: BackupData,
        settings: JsonObject,
        header: BackupHeader,
        output: OutputStream,
        onProgress: suspend (BackupProgress) -> Unit = {},
    ): BackupSummary {
        val counting = CountingOutputStream(output)
        val zip = ZipOutputStream(counting)
        val checksums = linkedMapOf<String, String>()
        val missing = mutableSetOf<String>()
        val total = data.photos.size
        onProgress(BackupProgress.Photos(0, total))
        data.photos.forEachIndexed { index, photo ->
            currentCoroutineContext().ensureActive()
            val bytes = readOrNull(photoFile(photo.id))
            if (bytes == null) {
                missing += photo.id
            } else {
                val name = BackupFormat.photoEntry(photo.id)
                zip.putStored(name, bytes, header.createdAt)
                checksums[name] = sha256(bytes)
            }
            onProgress(BackupProgress.Photos(index + 1, total))
        }
        currentCoroutineContext().ensureActive()
        onProgress(BackupProgress.Finishing)
        val written = data.withoutPhotos(missing)
        val json = BackupFormat.json
        val dataBytes = json.encodeToString(BackupData.serializer(), written).encodeToByteArray()
        val settingsBytes = json.encodeToString(JsonObject.serializer(), settings).encodeToByteArray()
        zip.putDeflated(BackupFormat.DATA, dataBytes, header.createdAt)
        checksums[BackupFormat.DATA] = sha256(dataBytes)
        zip.putDeflated(BackupFormat.SETTINGS, settingsBytes, header.createdAt)
        checksums[BackupFormat.SETTINGS] = sha256(settingsBytes)
        val manifest = BackupManifest(
            createdAt = header.createdAt,
            zoneId = header.zoneId,
            appVersionName = header.appVersionName,
            appVersionCode = header.appVersionCode,
            schemaVersion = header.schemaVersion,
            settingsSchemaVersion = header.settingsSchemaVersion,
            counts = written.counts(),
            checksums = checksums,
        )
        zip.putDeflated(
            BackupFormat.MANIFEST,
            json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray(),
            header.createdAt,
        )
        zip.finish()
        zip.flush()
        return BackupSummary(manifest, counting.count)
    }

    private fun readOrNull(file: File): ByteArray? = try {
        if (file.isFile) file.readBytes() else null
    } catch (_: FileNotFoundException) {
        null // deleted between the check and the read
    }
}

/** [this] without the photos [ids] and without the covers that point at them (05 §8). */
internal fun BackupData.withoutPhotos(ids: Set<String>): BackupData = if (ids.isEmpty()) {
    this
} else {
    copy(
        photos = photos.filterNot { it.id in ids },
        teas = teas.map { if (it.coverPhotoId in ids) it.copy(coverPhotoId = null) else it },
        vessels = vessels.map { if (it.coverPhotoId in ids) it.copy(coverPhotoId = null) else it },
    )
}

/** Lowercase hex SHA-256 of [bytes]. */
fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }

private fun ZipOutputStream.putStored(name: String, bytes: ByteArray, time: Long) {
    val crc = CRC32().apply { update(bytes) }
    putNextEntry(
        ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
            this.time = time
        },
    )
    write(bytes)
    closeEntry()
}

private fun ZipOutputStream.putDeflated(name: String, bytes: ByteArray, time: Long) {
    putNextEntry(
        ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
            this.time = time
        },
    )
    write(bytes)
    closeEntry()
}

/** Counts the bytes written through it; never closes [out]. */
private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
    var count = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }

    override fun flush() = out.flush()
}
