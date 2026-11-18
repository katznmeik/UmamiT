package com.umamit.core.backup.restore

import android.util.Log
import com.umamit.core.backup.archive.BackupContent
import com.umamit.core.backup.archive.BackupReadResult
import com.umamit.core.backup.archive.CreateBackup
import com.umamit.core.backup.archive.ReadBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * *Sicherung einspielen* (BAK-02, 05 §8 „Replace", D-130 Q5, items 21, 22, 26): replaces every personal table, the
 * photos and the settings (but the device-bound keys) with a validated [BackupContent], after a safety copy of the
 * data before.
 *
 * 1. Refused while a timer is active (`active_brew` in any slot, or [TimerActivity]) or a catalog refresh runs.
 * 2. An unfinished earlier replace is finished first, then the expiry runs; then the safety copy is written to
 *    `umamit-safety.new` with the export's writer and read back with the reader. If either fails it is deleted and the
 *    import refused; the previous copy and its `safetyCopyAt` stay.
 * 3.–5. [ImportReplacer.replace]: one Room transaction, the photo folder swap, one settings write, then the copy
 *    renamed over `umamit-safety.umamit` and `safetyCopyAt` = now. The install marker is never touched, so the device
 *    restore detection does not run.
 *
 * It runs to its end once started (the screen has no back, 03 SCR-26); the caller closes [BackupContent] afterwards.
 */
class RestoreBackup @Inject constructor(
    private val replacer: ImportReplacer,
    private val createBackup: CreateBackup,
    private val readBackup: ReadBackup,
    private val expiry: SafetyCopyExpiry,
) {
    private val paths get() = replacer.paths

    suspend fun restore(content: BackupContent, timer: TimerActivity): RestoreResult =
        withContext(Dispatchers.IO + NonCancellable) {
            replacer.mutex.withLock { restoreLocked(content, timer) }
        }

    private suspend fun restoreLocked(content: BackupContent, timer: TimerActivity): RestoreResult {
        val refusal = replacer.refusal(timer)
            ?: RestoreRefusal.REPLACE_FAILED.takeUnless { replacer.finishPending() }
            ?: run {
                expiry.expireLocked()
                RestoreRefusal.SAFETY_COPY_FAILED.takeUnless { writeSafetyCopy() }
            }
        return if (refusal != null) {
            RestoreResult.Refused(refusal)
        } else {
            replacer.replace(ImportKind.IMPORT, content, timer)
        }
    }

    /** Step 2: the copy written, fsynced and read back; on any failure deleted again. */
    private suspend fun writeSafetyCopy(): Boolean {
        val ok = try {
            paths.safetyDir.mkdirs()
            FileOutputStream(paths.safetyNew).use { out ->
                createBackup.writeTo(out)
                out.fd.sync()
            }
            Durable.syncDir(paths.safetyDir)
            replacer.reached(ImportPoint.SAFETY_COPY_WRITTEN)
            when (val read = readBackup.read(paths.safetyNew, paths.check)) {
                is BackupReadResult.Valid -> {
                    read.content.close()
                    true
                }

                is BackupReadResult.Refused -> {
                    Log.w(TAG, "the safety copy does not read back: ${read.refusal}")
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // IO (no space), SQLite: all refuse the import
        ) {
            Log.w(TAG, "the safety copy could not be written", e)
            false
        }
        if (!ok) {
            paths.safetyNew.delete()
            paths.check.deleteRecursively()
        }
        return ok
    }

    private companion object {
        const val TAG = "UmamiTImport"
    }
}
