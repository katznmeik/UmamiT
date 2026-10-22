package com.umamit.core.data

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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one-time download of the two seed teas' product photos during the first-start setup
 * (ONB-06, network use (b) of GEN-01, D-036). It never blocks the setup and never shows an error.
 *
 * - **Polite:** every request goes through the allow-listed [ShopClient] (one connection,
 *   ≤ 2 requests/s, honest User-Agent). robots.txt is read first; a disallowed photo path is
 *   not fetched (a 4xx robots.txt allows everything, any other failure disallows everything,
 *   RFC 9309 §2.3.1).
 * - **Stops** on a Cloudflare challenge, HTTP 429, any `Retry-After` or a network error
 *   (offline): no further request, no retry, the placeholder stays ([SeedPhotoProgress.Failed]).
 * - **Stores** each image unchanged as `photos/<id>.jpg`, a `photo` row with origin
 *   `CATALOG_COPY` (05 §3.9), and the tea's cover. Teas that are gone or already have a cover
 *   are left out; an answer that is not a JPEG is left out.
 * - **Runs once:** when it ends in any way (done, skipped, failed) the settings marker is set
 *   and it never runs again; CAT-15 fills in missing photos with the first catalog refresh (M2).
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
            outcome = coroutineScope {
                val job = async { if (skipRequested) Skipped else download() }
                work = job
                if (skipRequested) job.cancel()
                job.await()
            }
        } catch (_: CancellationException) {
            // Skipped by skip() or by the caller's cancellation; the caller's is rethrown below.
        } finally {
            work = null
            withContext(NonCancellable) { settings.markSeedPhotosAttempted() }
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

    private suspend fun download(): SeedPhotoProgress {
        val pending = SeedData.photoPaths.filter { (teaId, _) ->
            teas.observeTea(teaId).first().let { it != null && it.coverPhoto == null }
        }
        if (pending.isEmpty()) return Done(stored = 0)
        state.value = Running(current = 1, total = pending.size)

        val robotsAnswer = client.get(client.resolve(RobotsTxt.ROBOTS_PATH))
        var stop = robotsAnswer.stopCause()
        val robots = robotsAnswer.toRobotsTxt()
        var stored = 0
        for ((index, photo) in pending.withIndex()) {
            if (stop != null) break
            state.value = Running(current = index + 1, total = pending.size)
            val (teaId, path) = photo
            val url = client.resolve(path)
            // A disallowed path is never fetched; a 404 or a non-JPEG leaves only this photo out.
            if (robots.isAllowed(url)) {
                val answer = client.get(url)
                stop = answer.stopCause()
                if (answer is ShopResponse.Success && store(teaId, answer.body)) stored++
            }
        }
        return stop?.let(::Failed) ?: Done(stored)
    }

    /** The cause that ends the whole run after [this] answer, or null to go on (08 §2.8). */
    private fun ShopResponse.stopCause(): FailureCause? = when (this) {
        is ShopResponse.Blocked -> when (reason) {
            BlockReason.CHALLENGE -> FailureCause.CHALLENGE
            BlockReason.TOO_MANY_REQUESTS -> FailureCause.TOO_MANY_REQUESTS
            BlockReason.RETRY_AFTER -> FailureCause.RETRY_AFTER
        }

        is ShopResponse.NetworkError -> FailureCause.NETWORK

        is ShopResponse.Success, is ShopResponse.HttpError -> null
    }

    /**
     * robots.txt from [this] answer (RFC 9309 §2.3.1): a 4xx means none exists, so everything is
     * allowed; any other failure means it is unreachable, so everything is disallowed.
     */
    private fun ShopResponse.toRobotsTxt(): RobotsTxt = when {
        this is ShopResponse.Success -> RobotsTxt.parse(body.decodeToString())
        this is ShopResponse.HttpError && code in HTTP_CLIENT_ERRORS -> RobotsTxt.ALLOW_ALL
        else -> RobotsTxt.DISALLOW_ALL
    }

    /** Stores [bytes] as [teaId]'s cover; false when they are no JPEG or the tea is gone. */
    private suspend fun store(teaId: String, bytes: ByteArray): Boolean {
        val size = jpegSize(bytes) ?: return false
        val id = UUID.randomUUID().toString()
        // Once the image is in, a skip no longer interrupts file and row half-way.
        return withContext(NonCancellable + Dispatchers.IO) {
            photoFiles.write(id, bytes)
            var saved = false
            try {
                saved = db.withTransaction {
                    if (teas.getTea(teaId) == null) return@withTransaction false
                    teas.savePhoto(
                        Photo(
                            id = id,
                            owner = PhotoOwner.OfTea(teaId),
                            origin = PhotoOrigin.CATALOG_COPY,
                            widthPx = size.width,
                            heightPx = size.height,
                            byteSize = bytes.size.toLong(),
                        ),
                    )
                    teas.setCoverPhoto(teaId, id)
                    true
                }
            } finally {
                if (!saved) photoFiles.delete(id)
            }
            saved
        }
    }

    private companion object {
        val HTTP_CLIENT_ERRORS = 400..499
    }
}
