package com.umamit.core.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one lock of the replacing import (05 §8 „Replace", BAK-02): the import, its undo, the start gate's recovery,
 * the start-up housekeeping of the safety copy, and the seed teas' photo download (ONB-06) take it, so an import waits
 * for a running download (D-133 Q10), a download never writes a photo file into a `photos/` that the folder swap is
 * moving away, and no two of them rename or delete the import's trees at once (#349).
 *
 * It lives here, beside [PHOTOS_DIR], because `core:backup` (the import) depends on `core:data` (the download) and
 * both see `core:database` (06 §3). Not reentrant: nothing that holds it calls another holder.
 */
@Singleton
class ImportLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
