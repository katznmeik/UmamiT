package com.umamit.core.backup.archive

import com.umamit.core.backup.R
import com.umamit.core.database.dao.BackupRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** Why a backup file is refused (05 §8 „Validation", BAK-02 AC2); [messageRes] is its German text in `strings.xml`. */
enum class BackupRefusal(val messageRes: Int) {
    /** „Das ist keine UmamiT-Sicherung.": not a ZIP, no `manifest.json`, or not the format id `umamit-backup`. */
    NOT_A_BACKUP(R.string.backup_refused_not_a_backup),

    /** A `formatVersion`, `schemaVersion` or `settingsSchemaVersion` newer than this app's (D-130 Q3). */
    NEWER_VERSION(R.string.backup_refused_newer_version),

    /**
     * „Die Sicherung ist beschädigt und wurde nicht eingespielt.": a ZIP limit, an unsafe or unknown path, a checksum,
     * a JSON type, enum or range of 05 §3 and §6, a broken reference, or a photo that is not a JPEG of at most
     * 2048 px a side.
     */
    DAMAGED(R.string.backup_refused_damaged),
}

/** What [ReadBackup] gives: the validated, staged content, or one refusal. */
sealed interface BackupReadResult {
    /** Every check passed. The caller owns [content] and closes it (which deletes the staging). */
    class Valid(val content: BackupContent) : BackupReadResult

    /** Not taken: the UI shows [refusal]'s text; the staging is already deleted and nothing else was touched. */
    data class Refused(val refusal: BackupRefusal) : BackupReadResult
}

/**
 * BAK-02 AC3's preview („Sicherung vom 3. Nov. 2026 · 142 Sessions, 23 Tees, 61 Fotos"): the file's [createdAt]
 * (epoch ms) in its [zoneId], and the counts of the validated rows.
 */
data class BackupPreview(val createdAt: Long, val zoneId: String, val sessions: Int, val teas: Int, val photos: Int)

/**
 * A `.umamit` file that passed every check of 05 §8, staged for the replace (#336).
 *
 * - [data] is at the current schema (`UmamiDatabase.VERSION`): a file of an older `schemaVersion` was converted by
 *   05 §9's rules; [rows] are its Room entities, closed under their references.
 * - [settings] is `settings.json` at the current settings `schemaVersion` (05 §6), with every key except
 *   [BackupFormat.DEVICE_BOUND_SETTINGS_KEYS] (none is ever in it); every value decodes to `StoredSettings` and is
 *   within 05 §6's range. The replace overlays exactly these keys on the stored settings in one write.
 * - [photosDir] holds exactly one `<id>.jpg` per row of [data]'s `photos`, nothing else, shaped like
 *   `filesDir/photos/`, so the replace can rename it whole (to `photos.new/`).
 * - [manifest] is the file's manifest as written (its `schemaVersion` may be older than [data]'s).
 *
 * [close] deletes the staging, whatever is left of it (also after [photosDir] was moved away); it is idempotent.
 */
class BackupContent internal constructor(
    val manifest: BackupManifest,
    val data: BackupData,
    val settings: JsonObject,
    val photosDir: File,
    private val staging: File,
) : Closeable {
    val createdAt: Long get() = manifest.createdAt

    val preview = BackupPreview(
        createdAt = manifest.createdAt,
        zoneId = manifest.zoneId,
        sessions = data.sessions.size,
        teas = data.teas.size,
        photos = data.photos.size,
    )

    /** The rows for the replace's one transaction (05 §8 „Replace" step 3). */
    val rows: BackupRows by lazy { data.toRows() }

    /** The staged JPEG of photo [id]. */
    fun photoFile(id: String): File = File(photosDir, "$id${BackupFormat.PHOTO_SUFFIX}")

    override fun close() {
        staging.deleteRecursively()
    }
}

/**
 * The backup reader (BAK-02 AC2, 05 §8 „Validation"): opens a picked file, which is untrusted input, and checks all of
 * it before anything changes. It reads and writes only the [staging] directory it is given (a private directory under
 * `noBackupFilesDir`, never `filesDir/photos/`): never `umamit.db`, `photos/` or DataStore.
 *
 * The verdict, whatever the order of the entries in the ZIP:
 * 1. not a ZIP (no local file header signature): [BackupRefusal.NOT_A_BACKUP];
 * 2. the central directory: more than 5 000 entries, a name that is absolute, has `..`, a backslash, a drive, a
 *    control character or is not unique, declared sizes over the limits: [BackupRefusal.DAMAGED];
 * 3. no `manifest.json`, or one that is not a JSON object with `format` `umamit-backup`:
 *    [BackupRefusal.NOT_A_BACKUP];
 * 4. a `formatVersion`, `schemaVersion` or `settingsSchemaVersion` newer than the app's:
 *    [BackupRefusal.NEWER_VERSION] (before any other entry is read, since a newer file may have other entries);
 * 5. anything else ([BackupFileReader], [BackupConversion], [BackupValidator], [BackupSettingsReader]):
 *    [BackupRefusal.DAMAGED].
 *
 * Every entry is read through one counting loop: per-entry and total uncompressed bytes as read, and at most
 * 100 bytes per compressed byte of the entry, so a ZIP bomb stops after a small write. On a refusal, a failure or
 * cancellation the staging is deleted.
 */
class ReadBackup internal constructor(private val limits: BackupLimits, private val onStaged: (bytes: Long) -> Unit) {
    @Inject
    constructor() : this(BackupLimits(), {})

    /**
     * Copies the picked document's [input] to [staging] and reads it there. [staging]'s old contents are deleted
     * first. Throws [IOException] when [input] cannot be read or [staging] cannot be written (e.g. no space), after
     * deleting the staging; [input] is not closed.
     */
    suspend fun read(input: InputStream, staging: File): BackupReadResult = withContext(Dispatchers.IO) {
        prepare(staging)
        guarded(staging) {
            val source = File(staging, SOURCE)
            copySource(input, source)
            BackupFileReader(source, staging, limits, onStaged).read().also { source.delete() }
        }
    }

    /**
     * Reads the backup [file] in place (the safety copy, #336), staging its photos in [staging], whose old contents
     * are deleted first. Throws [IOException] when [staging] cannot be written, after deleting it.
     */
    suspend fun read(file: File, staging: File): BackupReadResult = withContext(Dispatchers.IO) {
        prepare(staging)
        guarded(staging) { BackupFileReader(file, staging, limits, onStaged).read() }
    }

    private fun prepare(staging: File) {
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Cannot create the backup staging")
    }

    /** [block]'s content, or its refusal; the staging is deleted unless a content is handed out. */
    private suspend fun guarded(staging: File, block: suspend () -> BackupContent): BackupReadResult {
        var result: BackupReadResult? = null
        try {
            result = try {
                BackupReadResult.Valid(block())
            } catch (refused: BackupRefusedException) {
                BackupReadResult.Refused(refused.refusal)
            }
        } finally {
            if (result !is BackupReadResult.Valid) staging.deleteRecursively()
        }
        return result
    }

    /** [input] to [target], refused unwritten when it does not start like a ZIP, and refused beyond the limits. */
    private fun copySource(input: InputStream, target: File) {
        val head = input.readNBytes(ZIP_MAGIC.size)
        if (!head.contentEquals(ZIP_MAGIC)) refuse(BackupRefusal.NOT_A_BACKUP)
        val max = limits.maxTotalBytes + limits.maxEntries * ZIP_OVERHEAD_PER_ENTRY
        FileOutputStream(target).use { out ->
            out.write(head)
            var count = head.size.toLong()
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                count += read
                if (count > max) refuse(BackupRefusal.DAMAGED)
                out.write(buffer, 0, read)
            }
        }
    }

    private companion object {
        const val SOURCE = "source.umamit"
        const val BUFFER_BYTES = 64 * 1024

        /** Headers and the central directory record of one entry, generously (its name is at most 64 + 11 chars). */
        const val ZIP_OVERHEAD_PER_ENTRY = 1_024L
    }
}
