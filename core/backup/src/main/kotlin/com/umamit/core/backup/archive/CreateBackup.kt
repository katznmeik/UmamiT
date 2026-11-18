package com.umamit.core.backup.archive

import com.umamit.core.common.WallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.datastore.StoredSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** The app's version, for the manifest (05 §8). */
data class AppVersion(val name: String, val code: Long)

/** The folder of the `photo` table's files, `filesDir/photos/` (05 §3.9). */
data class BackupPhotoDirectory(val dir: File)

/** The size note before the picker (BAK-01): „Etwa 18 MB · 142 Sessions, 23 Tees, 61 Fotos". */
data class BackupEstimate(val sessions: Int, val teas: Int, val photos: Int, val bytes: Long)

/** How an export ([CreateBackup.export]) goes: progress, then [Done] or [Failed]. */
sealed interface BackupExport {
    data class Progress(val progress: BackupProgress) : BackupExport

    /** The file is complete and `lastBackupAt` is set. */
    data class Done(val summary: BackupSummary) : BackupExport

    /** The write failed; the file is incomplete and the caller deletes the document where it can. */
    data class Failed(val cause: Exception) : BackupExport
}

/**
 * *Sicherung erstellen* (BAK-01, 05 §8, 06 §2.2). The rows are read in one read transaction, so a timer writing
 * meanwhile cannot tear the file, and it is allowed while a timer is active (D-130 Q8); the photos are copied after
 * it. [export] is the user's export and sets `lastBackupAt`; [writeTo] writes the same file without it, for the
 * safety copy (BAK-02, #336).
 */
class CreateBackup @Inject constructor(
    private val db: UmamiDatabase,
    private val settings: SettingsDataSource,
    private val photos: BackupPhotoDirectory,
    private val appVersion: AppVersion,
    private val clock: WallClock,
) {
    private val writer = BackupWriter { File(photos.dir, "$it${BackupFormat.PHOTO_SUFFIX}") }

    /**
     * The counts and the size of a backup now: the photo files' bytes plus the JSON entries as written (uncompressed,
     * so never below what deflating gives) and a manifest allowance.
     */
    suspend fun estimate(): BackupEstimate = withContext(Dispatchers.IO) {
        val data = db.backupDao().snapshot().toBackup()
        val json = BackupFormat.json
        val jsonBytes = json.encodeToString(BackupData.serializer(), data).encodeToByteArray().size.toLong() +
            json.encodeToString(JsonObject.serializer(), settingsJson(settings.stored())).encodeToByteArray().size +
            MANIFEST_BASE_BYTES + MANIFEST_BYTES_PER_ENTRY * (data.photos.size + 2)
        val photoBytes = data.photos.sumOf { File(photos.dir, "${it.id}${BackupFormat.PHOTO_SUFFIX}").length() }
        BackupEstimate(
            sessions = data.sessions.size,
            teas = data.teas.size,
            photos = data.photos.size,
            bytes = jsonBytes + photoBytes,
        )
    }

    /**
     * The user's export to [output] (the picker's document): [BackupExport.Progress] while it runs, then
     * [BackupExport.Done] once the file is complete and `lastBackupAt` is set, or [BackupExport.Failed]. Cancelling
     * the collection stops the write before the next photo and sets nothing. [output] is not closed.
     */
    fun export(output: OutputStream): Flow<BackupExport> = channelFlow {
        val summary = try {
            writeTo(output) { send(BackupExport.Progress(it)) }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // IO, SQLite, a full disk: all are "failed"
        ) {
            send(BackupExport.Failed(e))
            return@channelFlow
        }
        // „N neue Sessions" counts from the snapshot's time, so a session started during the write still counts.
        settings.markLastBackupAt(summary.manifest.createdAt)
        send(BackupExport.Done(summary))
    }.buffer(Channel.RENDEZVOUS) // the write waits for the collector, so a cancel stops it at the next photo

    /**
     * Writes the backup to [output] without touching any setting: the safety copy's write (BAK-02). Throws what the
     * write throws; cancellable between photos. [output] is not closed.
     */
    suspend fun writeTo(output: OutputStream, onProgress: suspend (BackupProgress) -> Unit = {}): BackupSummary =
        withContext(Dispatchers.IO) {
            onProgress(BackupProgress.Reading)
            val createdAt = clock.nowMs()
            val rows = db.backupDao().snapshot()
            val stored = settings.stored()
            writer.write(
                data = rows.toBackup(),
                settings = settingsJson(stored),
                header = BackupHeader(
                    createdAt = createdAt,
                    zoneId = clock.zone().id,
                    appVersionName = appVersion.name,
                    appVersionCode = appVersion.code,
                    schemaVersion = UmamiDatabase.VERSION,
                    settingsSchemaVersion = stored.schemaVersion,
                ),
                output = output,
                onProgress = onProgress,
            )
        }

    private companion object {
        const val MANIFEST_BASE_BYTES = 1_024L
        const val MANIFEST_BYTES_PER_ENTRY = 100L
    }
}

/** `settings.json` (05 §8): every stored key of 05 §6 but [BackupFormat.DEVICE_BOUND_SETTINGS_KEYS]. */
fun settingsJson(stored: StoredSettings): JsonObject {
    val all = BackupFormat.json.encodeToJsonElement(StoredSettings.serializer(), stored).jsonObject
    return JsonObject(all.filterKeys { it !in BackupFormat.DEVICE_BOUND_SETTINGS_KEYS })
}
