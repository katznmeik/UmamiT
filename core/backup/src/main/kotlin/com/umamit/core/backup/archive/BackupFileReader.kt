package com.umamit.core.backup.archive

import android.graphics.BitmapFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * The ZIP limits of 05 §8 „Validation" (Claude's numbers, far above what 06 §5.2's photos and ten years of sessions
 * produce). Sizes are uncompressed bytes as read, never only as declared. The JSON entries are parsed into a tree, so
 * their caps keep that tree within the heap (D-133 item 13): 64 MB for `data.json` (ten years of sessions are about
 * 10 MB), 1 MB for `manifest.json` and `settings.json`.
 */
internal data class BackupLimits(
    val maxEntries: Int = 5_000,
    val maxTotalBytes: Long = 2_000_000_000L,
    val maxPhotoBytes: Long = 20_000_000L,
    val maxDataBytes: Long = 64_000_000L,
    val maxSmallJsonBytes: Long = 1_000_000L,
    /** Uncompressed bytes per compressed byte of an entry, at most. */
    val maxRatio: Long = 100L,
    val maxPhotoSide: Int = PHOTO_SIDE,
) {
    companion object {
        /** 06 §5.2: a photo is a JPEG of at most 2048 px a side. */
        const val PHOTO_SIDE = 2_048
    }
}

/** One refusal, thrown inside the reader and turned into [BackupReadResult.Refused] by [ReadBackup]. */
internal class BackupRefusedException(val refusal: BackupRefusal) : Exception(refusal.name)

internal fun refuse(refusal: BackupRefusal): Nothing = throw BackupRefusedException(refusal)

/** Refuses the file as damaged unless [condition] holds. */
internal fun ensure(condition: Boolean) {
    if (!condition) refuse(BackupRefusal.DAMAGED)
}

/** A ZIP's local file header signature, `PK\3\4`: every non-empty ZIP starts with it. */
internal val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/**
 * The entry paths of [BackupFormat] and nothing else (zip-slip, 05 §8). A photo id is the file name of the staged
 * photo, so it is ASCII letters, digits, `-` and `_` only, at most 64 characters (UUIDs and the seed ids are).
 */
internal object BackupPaths {
    private val PHOTO_ID = Regex("[A-Za-z0-9_-]{1,64}")
    private val DRIVE = Regex("^[A-Za-z]:")

    fun isPhotoId(id: String) = PHOTO_ID.matches(id)

    /**
     * A relative path without `..`, backslash, drive, control characters or empty segments; a folder entry (a
     * trailing `/`) is safe, though not [isKnown].
     */
    fun isSafe(name: String): Boolean = name.isNotEmpty() &&
        !name.startsWith("/") &&
        '\\' !in name &&
        !DRIVE.containsMatchIn(name) &&
        name.none { it.isISOControl() } &&
        name.removeSuffix("/").split('/').none { it.isEmpty() || it == "." || it == ".." }

    fun isKnown(name: String): Boolean = name == BackupFormat.MANIFEST ||
        name == BackupFormat.DATA ||
        name == BackupFormat.SETTINGS ||
        photoId(name) != null

    /** The photo id of a `photos/<id>.jpg` path, or null. */
    fun photoId(name: String): String? = name.takeIf { it.startsWith(BackupFormat.PHOTOS_DIR) }
        ?.removePrefix(BackupFormat.PHOTOS_DIR)
        ?.takeIf { it.endsWith(BackupFormat.PHOTO_SUFFIX) }
        ?.removeSuffix(BackupFormat.PHOTO_SUFFIX)
        ?.takeIf(::isPhotoId)
}

/**
 * Reads one `.umamit` file ([source]) into [staging] in the order of [ReadBackup]'s KDoc. Each entry is read at most
 * once, through [extract]; the JSON entries go to temporary files in [staging] (never a 200 MB string), the photos
 * to `staging/photos/<id>.jpg` with names built from the validated row ids, never from the entry names.
 */
internal class BackupFileReader(
    private val source: File,
    private val staging: File,
    private val limits: BackupLimits,
    private val onStaged: (Long) -> Unit,
) {
    private var totalBytes = 0L
    private var stagedBytes = 0L

    suspend fun read(): BackupContent {
        if (!startsLikeZip()) refuse(BackupRefusal.NOT_A_BACKUP)
        val zip = try {
            ZipFile(source)
        } catch (_: IOException) {
            refuse(BackupRefusal.DAMAGED) // has the signature, but no readable central directory
        }
        return zip.use { read(it) }
    }

    private fun startsLikeZip(): Boolean = FileInputStream(source).use { it.readNBytes(ZIP_MAGIC.size) }
        .contentEquals(ZIP_MAGIC)

    private suspend fun read(zip: ZipFile): BackupContent {
        val entries = entries(zip)
        val manifestTree = manifestTree(zip, entries)
        ManifestRules.checkVersions(manifestTree)
        ensure(entries.keys.all(BackupPaths::isKnown))
        val manifest = BackupJson.decodeStrict(BackupManifest.serializer(), manifestTree)
        ManifestRules.check(manifest, entries.keys)

        val data = BackupConversion.data(jsonObject(zip, entries, BackupFormat.DATA, manifest), manifest.schemaVersion)
        BackupValidator.check(data)
        ensure(data.counts() == manifest.counts)
        val settings = BackupSettingsReader.read(
            jsonObject(zip, entries, BackupFormat.SETTINGS, manifest),
            manifest.settingsSchemaVersion,
        )

        val photoEntries = entries.keys.mapNotNull(BackupPaths::photoId).toSet()
        ensure(photoEntries == data.photos.map { it.id }.toSet())
        val photosDir = File(staging, PHOTOS).apply { mkdirs() }
        data.photos.forEach { photo ->
            currentCoroutineContext().ensureActive()
            val name = BackupFormat.photoEntry(photo.id)
            val file = File(photosDir, "${photo.id}${BackupFormat.PHOTO_SUFFIX}")
            ensure(extract(zip, entries.getValue(name), file, limits.maxPhotoBytes) == manifest.checksums[name])
            ensure(isJpeg(file))
        }
        return BackupContent(manifest, data, settings, photosDir, staging)
    }

    /**
     * The central directory: count, safe and unique names, declared sizes (refused before anything is read). The
     * compressed sizes together fit in the file, so a forged compressed size (or entries sharing one block of data)
     * cannot raise [extract]'s ratio cap beyond 100 × the file's size.
     */
    private fun entries(zip: ZipFile): Map<String, ZipEntry> {
        val entries = LinkedHashMap<String, ZipEntry>()
        var declared = 0L
        var compressed = 0L
        for (entry in zip.entries()) {
            ensure(entries.size < limits.maxEntries)
            ensure(BackupPaths.isSafe(entry.name))
            ensure(entries.put(entry.name, entry) == null)
            if (entry.size >= 0) {
                ensure(entry.size <= maxBytes(entry.name))
                declared += entry.size
                ensure(declared <= limits.maxTotalBytes)
            }
            compressed += entry.compressedSize.coerceAtLeast(0)
            ensure(compressed <= source.length())
        }
        return entries
    }

    private fun maxBytes(name: String): Long = when {
        BackupPaths.photoId(name) != null -> limits.maxPhotoBytes
        name == BackupFormat.DATA -> limits.maxDataBytes
        else -> limits.maxSmallJsonBytes
    }

    /** `manifest.json` as a JSON object with the backup's format id, or not a backup. */
    private fun manifestTree(zip: ZipFile, entries: Map<String, ZipEntry>): JsonObject {
        val entry = entries[BackupFormat.MANIFEST] ?: refuse(BackupRefusal.NOT_A_BACKUP)
        val tree = try {
            parseManifest(zip, entry) as? JsonObject
        } catch (_: BackupRefusedException) {
            null // unreadable as JSON: not our manifest
        } ?: refuse(BackupRefusal.NOT_A_BACKUP)
        if (!ManifestRules.isBackup(tree)) refuse(BackupRefusal.NOT_A_BACKUP)
        return tree
    }

    /** The JSON object of entry [name] whose bytes match the manifest's checksum. */
    private fun jsonObject(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        name: String,
        manifest: BackupManifest,
    ): JsonObject {
        val temp = File(staging, "$name$TEMP_SUFFIX")
        try {
            ensure(extract(zip, entries.getValue(name), temp, maxBytes(name)) == manifest.checksums[name])
            return BackupJson.parse(temp) as? JsonObject ?: refuse(BackupRefusal.DAMAGED)
        } finally {
            temp.delete()
        }
    }

    private fun parseManifest(zip: ZipFile, entry: ZipEntry): JsonElement {
        val temp = File(staging, "${BackupFormat.MANIFEST}$TEMP_SUFFIX")
        try {
            extract(zip, entry, temp, limits.maxSmallJsonBytes)
            return BackupJson.parse(temp)
        } finally {
            temp.delete()
        }
    }

    /**
     * Streams [entry] to [target] and returns the SHA-256 of its bytes. Refused as soon as the bytes read exceed
     * [max], the entry's compressed size × [BackupLimits.maxRatio] or, with all entries so far,
     * [BackupLimits.maxTotalBytes]; and when it ends at another size than declared. A ZIP error while reading is
     * damage; a failed write to [target] (no space) is thrown.
     */
    private fun extract(zip: ZipFile, entry: ZipEntry, target: File, max: Long): String {
        val compressed = entry.compressedSize.coerceAtLeast(0)
        val cap = if (compressed > max / limits.maxRatio) max else minOf(max, compressed * limits.maxRatio)
        ensure(entry.size <= cap)
        val digest = MessageDigest.getInstance("SHA-256")
        val input = try {
            zip.getInputStream(entry)
        } catch (_: IOException) {
            refuse(BackupRefusal.DAMAGED)
        }
        var count = 0L
        input.use {
            FileOutputStream(target).use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = try {
                        input.read(buffer)
                    } catch (_: IOException) {
                        refuse(BackupRefusal.DAMAGED) // ZipException, EOFException: the entry's data is broken
                    }
                    if (read < 0) break
                    count += read
                    totalBytes += read
                    ensure(count <= cap && totalBytes <= limits.maxTotalBytes)
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                    stagedBytes += read
                    onStaged(stagedBytes)
                }
            }
        }
        ensure(entry.size < 0 || count == entry.size)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 06 §5.2: a JPEG of 1 to [BackupLimits.maxPhotoSide] px a side (a bounds decode). */
    private fun isJpeg(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return options.outMimeType == JPEG_MIME &&
            options.outWidth in 1..limits.maxPhotoSide &&
            options.outHeight in 1..limits.maxPhotoSide
    }

    private companion object {
        const val PHOTOS = "photos"
        const val TEMP_SUFFIX = ".tmp"
        const val BUFFER_BYTES = 64 * 1024
        const val JPEG_MIME = "image/jpeg"
    }
}
