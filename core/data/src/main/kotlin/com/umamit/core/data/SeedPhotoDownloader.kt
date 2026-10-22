package com.umamit.core.data

import android.database.SQLException
import androidx.room.withTransaction
import com.umamit.core.data.SeedPhotoProgress.Done
import com.umamit.core.data.SeedPhotoProgress.Failed
import com.umamit.core.data.SeedPhotoProgress.FailureCause
import com.umamit.core.data.SeedPhotoProgress.Running
import com.umamit.core.data.SeedPhotoProgress.Skipped
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.Photo
import com.umamit.core.model.PhotoOrigin
import com.umamit.core.model.PhotoOwner
import com.umamit.core.network.BlockReason
import com.umamit.core.network.RobotsTxt
import com.umamit.core.network.ShopClient
import com.umamit.core.network.ShopResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one-time download of the two seed teas' product photos during the first-start setup
 * (ONB-06, network use (b) of GEN-01, D-036, D-050). It never blocks the setup and never shows
 * an error or throws.
 *
 * - **Runs once:** the settings marker is set **before the first request**, so neither a skip,
 *   a failure nor a crash or kill mid-run ever repeats it; CAT-15 fills in missing photos with
 *   the first catalog refresh (M2).
 * - **Polite:** every request goes through the allow-listed [ShopClient] (one connection,
 *   ≤ 2 requests/s, honest User-Agent). robots.txt is read first and a disallowed photo path
 *   is not fetched. A 404/410 robots.txt allows everything; a 401/403/5xx stops the run; any
 *   other failure disallows everything (RFC 9309 §2.3.1).
 * - **Stops** on a Cloudflare challenge or block page, HTTP 429, any `Retry-After`, a 401/403/5xx,
 *   a network error (offline) or a storage error: no further request, no retry, the placeholder
 *   stays ([SeedPhotoProgress.Failed]). Photos stored before stay.
 * - **Stores** each image decoded and re-encoded ([PhotoTranscoder]) as `photos/<id>.jpg`, a
 *   `photo` row with origin `CATALOG_COPY` (05 §3.9), and the tea's cover. Teas that are gone
 *   or already have a cover are left out, and so is an answer that is no complete JPEG.
 *
 * A singleton, so the setup screen observes the same [progress] that the run updates.
 */
@Singleton
class SeedPhotoDownloader @Inject constructor(
    private val client: ShopClient,
    private val db: UmamiDatabase,
    private val teas: TeaRepository,
    private val settings: SettingsRepository,
    private val photoFiles: PhotoFiles,
) {
    private val state = MutableStateFlow<SeedPhotoProgress>(SeedPhotoProgress.Idle)

    /** Idle → Running(1 of n) … → Done / Skipped / Failed, for SCR-01 (M1-15). */
    val progress: StateFlow<SeedPhotoProgress> = state.asStateFlow()

    private val started = AtomicBoolean(false)

    @Volatile
    private var skipRequested = false

    @Volatile
    private var work: Job? = null

    /** Whether the download has never run on this install (the setup shows the step only then). */
    suspend fun isNeeded(): Boolean = !settings.seedPhotosAttempted.first()

    /**
     * Runs the download unless it already ran (then nothing is sent and the current [progress]
     * is returned, [SeedPhotoProgress.Idle] in a new process). Returns the final progress.
     * Cancelling the caller has the same effect as [skip].
     */
    suspend fun downloadIfNeeded(): SeedPhotoProgress {
        if (!isNeeded() || !started.compareAndSet(false, true)) return state.value
        var outcome: SeedPhotoProgress = Skipped
        try {
            outcome = if (recordAttempt()) runCancellable() else Failed(FailureCause.STORAGE)
        } catch (_: CancellationException) {
            // Skipped by the caller's cancellation, which is rethrown below.
        } finally {
            state.value = outcome
        }
        currentCoroutineContext().ensureActive()
        return outcome
    }

    /** *Überspringen*: stops the running download (or the one about to start) as skipped. */
    fun skip() {
        skipRequested = true
        work?.cancel()
    }

    /** Sets the marker before anything is sent; false when it can't be written. */
    private suspend fun recordAttempt(): Boolean = try {
        withContext(NonCancellable) { settings.markSeedPhotosAttempted() }
        true
    } catch (_: IOException) {
        false
    }

    /** [download] in a child that [skip] can cancel; a skip ends as [Skipped]. */
    private suspend fun runCancellable(): SeedPhotoProgress = try {
        coroutineScope {
            val job = async { if (skipRequested) Skipped else download() }
            work = job
            if (skipRequested) job.cancel()
            job.await()
        }
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        if (skipRequested) Skipped else throw e
    } finally {
        work = null
    }

    private suspend fun download(): SeedPhotoProgress {
        val pending = SeedData.photoPaths.filter { (teaId, _) ->
            teas.observeTea(teaId).first().let { it != null && it.coverPhoto == null }
        }
        if (pending.isEmpty()) return Done(stored = 0)
        state.value = Running(current = 1, total = pending.size)

        val robotsAnswer = client.get(client.resolve(RobotsTxt.ROBOTS_PATH))
        var stop = robotsAnswer.stopCause()
        val robots = withContext(Dispatchers.Default) { robotsAnswer.toRobotsTxt() }
        var stored = 0
        for ((index, photo) in pending.withIndex()) {
            if (stop != null) break
            state.value = Running(current = index + 1, total = pending.size)
            val (teaId, path) = photo
            val url = client.resolve(path)
            // A disallowed path is never fetched; a 404 or a bad image leaves only this photo out.
            if (withContext(Dispatchers.Default) { robots.isAllowed(url) }) {
                val answer = client.get(url)
                stop = answer.stopCause()
                if (answer is ShopResponse.Success) {
                    when (store(teaId, answer.body)) {
                        StoreResult.STORED -> stored++
                        StoreResult.LEFT_OUT -> Unit
                        StoreResult.FAILED -> stop = FailureCause.STORAGE
                    }
                }
            }
        }
        return stop?.let(::Failed) ?: Done(stored)
    }

    /** The cause that ends the whole run after [this] answer, or null to go on (08 §2.8, D-050). */
    private fun ShopResponse.stopCause(): FailureCause? = when (this) {
        is ShopResponse.Blocked -> when (reason) {
            BlockReason.CHALLENGE -> FailureCause.CHALLENGE
            BlockReason.BLOCKED -> FailureCause.BLOCKED
            BlockReason.TOO_MANY_REQUESTS -> FailureCause.TOO_MANY_REQUESTS
            BlockReason.RETRY_AFTER -> FailureCause.RETRY_AFTER
        }

        is ShopResponse.NetworkError -> FailureCause.NETWORK

        is ShopResponse.HttpError -> FailureCause.HTTP_ERROR.takeIf { code in REFUSED || code in SERVER_ERRORS }

        is ShopResponse.Success -> null
    }

    /**
     * robots.txt from [this] answer (RFC 9309 §2.3.1): 404 and 410 mean none exists, so
     * everything is allowed; any other failure disallows everything (the stops end the run anyway).
     */
    private fun ShopResponse.toRobotsTxt(): RobotsTxt = when {
        this is ShopResponse.Success -> RobotsTxt.parse(body)
        this is ShopResponse.HttpError && code in MISSING -> RobotsTxt.ALLOW_ALL
        else -> RobotsTxt.DISALLOW_ALL
    }

    private enum class StoreResult { STORED, LEFT_OUT, FAILED }

    /** Stores [bytes] re-encoded as [teaId]'s cover. A disk or database error is [StoreResult.FAILED]. */
    private suspend fun store(teaId: String, bytes: ByteArray): StoreResult {
        val photo = withContext(Dispatchers.Default) { PhotoTranscoder.transcode(bytes) } ?: return StoreResult.LEFT_OUT
        val id = UUID.randomUUID().toString()
        // Once the image is in, a skip no longer interrupts file and row half-way.
        return withContext(NonCancellable + Dispatchers.IO) {
            var result = StoreResult.FAILED
            try {
                photoFiles.write(id, photo.bytes)
                result = if (saveCover(teaId, id, photo)) StoreResult.STORED else StoreResult.LEFT_OUT
            } catch (_: IOException) {
                // Disk full or not writable: FAILED.
            } catch (_: SQLException) {
                // Database error: FAILED.
            } finally {
                if (result != StoreResult.STORED) photoFiles.delete(id)
            }
            result
        }
    }

    /** Writes the photo row and the cover in one transaction; false when the tea is gone or has a cover. */
    private suspend fun saveCover(teaId: String, id: String, photo: EncodedPhoto): Boolean = db.withTransaction {
        val tea = teas.getTea(teaId)
        if (tea == null || tea.coverPhotoId != null) return@withTransaction false
        teas.savePhoto(
            Photo(
                id = id,
                owner = PhotoOwner.OfTea(teaId),
                origin = PhotoOrigin.CATALOG_COPY,
                widthPx = photo.width,
                heightPx = photo.height,
                byteSize = photo.bytes.size.toLong(),
            ),
        )
        teas.setCoverPhoto(teaId, id)
        true
    }

    private companion object {
        val MISSING = setOf(404, 410)
        val REFUSED = setOf(401, 403)
        val SERVER_ERRORS = 500..599
    }
}
