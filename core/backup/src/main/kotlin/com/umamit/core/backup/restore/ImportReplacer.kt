package com.umamit.core.backup.restore

import android.util.Log
import androidx.room.withTransaction
import com.umamit.core.backup.archive.BackupContent
import com.umamit.core.common.WallClock
import com.umamit.core.data.catalog.CatalogRefreshRunner
import com.umamit.core.data.catalog.CatalogRefreshState
import com.umamit.core.database.ImportLock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.datastore.SettingsDataSource
import com.umamit.core.model.TimerSlot
import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The replace that the import and its undo share (05 §8 „Replace" steps 1, 3–5; the D-130 build note of #336), under
 * the one [ImportLock] with the expiry's start-up housekeeping and the seed photo download (D-133 Q10).
 *
 * - [refusal]: step 1 and the PO's catalog refresh rule. `active_brew` is read through its DAO only (all three slots;
 *   the quick timer is its `QUICK` row), plus the in-memory [TimerActivity] (D-054).
 * - [replace]: [ImportEngine.prepare], the one Room transaction (the refusal again, deferred foreign keys, every table
 *   emptied and refilled, the commit token), a WAL checkpoint (once it completed the marker says `durable`), then
 *   [ImportEngine.rollForward]. A failure before the commit rolls back and refuses; one after it is retried once and
 *   else left to the next start.
 */
@Singleton
class ImportReplacer @Inject constructor(
    private val db: UmamiDatabase,
    private val settings: SettingsDataSource,
    internal val paths: ImportPaths,
    private val refresh: CatalogRefreshRunner,
    private val clock: WallClock,
    internal val lock: ImportLock,
) {
    /** The tests' failures and simulated crashes; a no-op in the app. */
    internal var checkpoints = ImportCheckpoints {}

    internal val engine =
        ImportEngine(paths, settings, clock, PhotoRows.of { db.openHelper.writableDatabase }) { checkpoints }

    internal fun reached(point: ImportPoint) = checkpoints.reached(point)

    internal val now: Long get() = clock.nowMs()

    internal suspend fun safetyCopyAt(): Long? = settings.stored().safetyCopyAt

    /** Why a replace may not start now, or null (05 §8 step 1, the PO's catalog refresh rule). */
    internal suspend fun refusal(timer: TimerActivity): RestoreRefusal? {
        if (refresh.state.value is CatalogRefreshState.Running) return RestoreRefusal.CATALOG_REFRESH
        val activeBrew = db.activeBrewDao()
        val timerActive = timer.isActive() || TimerSlot.entries.any { activeBrew.get(it) != null }
        return if (timerActive) RestoreRefusal.TIMER_ACTIVE else null
    }

    /** Finishes a replace an earlier failure left (the marker), with the open database's token; false if it failed. */
    internal suspend fun finishPending(): Boolean =
        engine.finishPending(token = { CommitToken.read(db.openHelper.writableDatabase) })

    /**
     * Steps 3–5 (5' for an undo) from [content]: the caller holds the lock, checked [refusal] and, for an import,
     * wrote the safety copy. [content] is not closed.
     */
    internal suspend fun replace(kind: ImportKind, content: BackupContent, timer: TimerActivity): RestoreResult =
        when (val commit = commitOrRollBack(kind, content, timer)) {
            is Commit.Refused -> RestoreResult.Refused(commit.refusal)

            is Commit.Done -> {
                reached(ImportPoint.COMMITTED)
                val marker = if (checkpointWal()) MarkerFile.markDurable(paths, commit.marker) else commit.marker
                RestoreResult.Done(content.preview, finishedAtNextStart = !finishAfterCommit(marker))
            }
        }

    /** The marker and the transaction; on a failure before the commit everything is rolled back. */
    private suspend fun commitOrRollBack(kind: ImportKind, content: BackupContent, timer: TimerActivity): Commit {
        var marker: ImportMarker? = null
        val failure = try {
            marker = engine.prepare(kind, content, CommitToken.read(db.openHelper.writableDatabase))
            transaction(marker, content, timer)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // IO, SQLite (a foreign key at the commit): all fail
        ) {
            e
        }
        return when {
            failure == null -> Commit.Done(checkNotNull(marker))

            failure is RefusedInTransaction -> rolledBack(kind, failure.refusal, null)

            marker == null -> rolledBack(kind, RestoreRefusal.REPLACE_FAILED, failure)

            // The database's token, not the exception, says whether the rows are in (05 §8 crash safety).
            else -> when (committed(marker)) {
                true -> Commit.Done(marker).also { Log.w(TAG, "committed before the failure; going on", failure) }

                false -> rolledBack(kind, RestoreRefusal.REPLACE_FAILED, failure)

                // The marker stays; the start gate reads the token on its own connection and decides.
                null -> Commit.Refused(RestoreRefusal.REPLACE_FAILED)
            }
        }
    }

    /** Whether [marker]'s transaction committed, by the database's token; null when the token cannot be read. */
    private fun committed(marker: ImportMarker): Boolean? = try {
        CommitToken.read(db.openHelper.writableDatabase) == marker.token
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception, // SQLite: unknown, so nothing is decided here
    ) {
        Log.w(TAG, "commit token unreadable", e)
        null
    }

    private suspend fun transaction(marker: ImportMarker, content: BackupContent, timer: TimerActivity) {
        val rows = content.rows
        db.withTransaction {
            // A timer started while the safety copy was written: its row (or the refresh) stops the replace here.
            refusal(timer)?.let { throw RefusedInTransaction(it) }
            val sqlite = db.openHelper.writableDatabase
            sqlite.execSQL("PRAGMA defer_foreign_keys = ON") // tea and photo point at each other; checked at the commit
            db.restoreDao().replaceAll(rows)
            // The deferred check before the commit: a COMMIT that fails on a foreign key leaves SQLite's transaction
            // open, and the connection with it; a throw here rolls back cleanly instead.
            val violations = sqlite.query("PRAGMA foreign_key_check").use { it.count }
            check(violations == 0) { "$violations rows of the backup break a foreign key" }
            CommitToken.set(sqlite, marker.token)
            reached(ImportPoint.IN_TRANSACTION)
        }
    }

    /**
     * Makes the commit durable before the first rename, so a power loss cannot leave new photos beside old rows; true
     * when the checkpoint completed.
     */
    private suspend fun checkpointWal(): Boolean {
        /** True: done; false: busy; null: failed. */
        fun checkpoint(): Boolean? = try {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                !(cursor.moveToFirst() && cursor.getInt(0) != 0)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // SQLite: the commit stands, only less durable
        ) {
            Log.w(TAG, "WAL checkpoint failed", e)
            null
        }
        var done = checkpoint()
        var tries = 1
        while (done == false && tries < CHECKPOINT_TRIES) {
            delay(CHECKPOINT_RETRY_MS)
            done = checkpoint()
            tries++
        }
        if (done == false) Log.w(TAG, "WAL checkpoint busy; the commit is in the WAL")
        return done == true
    }

    /** True when every step after the commit is done; else retried once, then the marker waits for the next start. */
    private suspend fun finishAfterCommit(marker: ImportMarker): Boolean {
        repeat(FORWARD_TRIES) { attempt ->
            try {
                // The marker on disk may be further on than [marker]; it is this replace's, and it committed.
                engine.rollForward(MarkerFile.read(paths) ?: marker)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception, // IO, DataStore: the next try (or start) finishes
            ) {
                Log.w(TAG, "a step after the commit failed (try ${attempt + 1})", e)
            }
        }
        return false
    }

    private fun rolledBack(kind: ImportKind, refusal: RestoreRefusal, cause: Exception?): Commit {
        cause?.let { Log.w(TAG, "replace failed before its commit; rolled back", it) }
        try {
            engine.rollBack(kind)
        } catch (e: IOException) {
            // The marker (if written) stays, so the next try or start rolls back what is left; the data is the old.
            Log.w(TAG, "roll back incomplete", e)
        }
        return Commit.Refused(refusal)
    }

    /** Whether the transaction committed ([Done]) or everything was rolled back ([Refused]). */
    private sealed interface Commit {
        class Done(val marker: ImportMarker) : Commit

        class Refused(val refusal: RestoreRefusal) : Commit
    }

    /** A refusal found inside the transaction, which rolls it back. */
    private class RefusedInTransaction(val refusal: RestoreRefusal) : Exception()

    private companion object {
        const val TAG = "UmamiTImport"
        const val CHECKPOINT_TRIES = 5
        const val FORWARD_TRIES = 2 // the steps after the commit, then once more (D-130 build note, the PO's answer)
        const val CHECKPOINT_RETRY_MS = 50L
    }
}

/** 7 × 24 h (D-130 Q5): how long the safety copy can be undone, in ms of wall time (not calendar days). */
internal const val SAFETY_COPY_VALID_MS = 7L * 24 * 60 * 60 * 1000
