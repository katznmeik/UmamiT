package com.umamit.core.backup.restore

import android.util.Log
import com.umamit.core.datastore.SettingsDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * The safety copy's expiry (05 §8 „Expiry", D-130 Q5): at each app start (after the start gate) and before an import or
 * an undo, a copy with now ≥ `safetyCopyAt` + 7 × 24 h, or without `safetyCopyAt`, is deleted and the key cleared; a
 * `safetyCopyAt` whose copy is gone is cleared too. Never while a replace's marker waits (its step 5 sets the two).
 */
class SafetyCopyExpiry @Inject constructor(
    private val replacer: ImportReplacer,
    private val settings: SettingsDataSource,
) {
    private val paths get() = replacer.paths

    /** At app start, off the main thread: what a crash left of a replace (with no marker), then the expiry. */
    suspend fun atAppStart() = withContext(Dispatchers.IO) {
        replacer.mutex.withLock {
            try {
                replacer.engine.cleanLeftovers()
            } catch (_: IOException) {
                // Left for the next start; a new import deletes the same folders before it begins.
            }
            expireLocked()
        }
    }

    /**
     * The expiry; the caller holds [ImportReplacer.mutex]. A copy that cannot be deleted stays with its key (the undo
     * still checks the 7 × 24 h itself) and is tried again next time.
     */
    internal suspend fun expireLocked() {
        if (paths.marker.exists()) return
        try {
            val at = settings.stored().safetyCopyAt
            val copy = paths.safetyCopy
            val expired = copy.exists() && (at == null || replacer.now >= at + SAFETY_COPY_VALID_MS)
            when {
                expired -> if (copy.delete()) settings.setSafetyCopyAt(null)
                !copy.exists() && at != null -> settings.setSafetyCopyAt(null)
            }
        } catch (e: IOException) {
            Log.w(TAG, "safety copy expiry not done", e)
        }
    }

    private companion object {
        const val TAG = "UmamiTImport"
    }
}
