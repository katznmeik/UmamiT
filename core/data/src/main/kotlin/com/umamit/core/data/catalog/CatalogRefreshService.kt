package com.umamit.core.data.catalog

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The refresh as [CatalogRefreshService] sees it: its state, *Abbrechen* and the diagnostics log. */
interface CatalogRefreshServicePort {
    val state: StateFlow<CatalogRefreshState>

    /** *Abbrechen* in the notification, or the end of the `dataSync` time budget. */
    fun cancel()

    /** One line in the diagnostics log (06 §5.6). */
    fun log(message: String)
}

/**
 * The app hands [CatalogRefreshService] its refresh: the system creates the service, so it is
 * not injected (the timer's `TimerHost` pattern, D-057).
 */
interface CatalogRefreshHost {
    val catalogRefresh: CatalogRefreshServicePort
}

/**
 * The catalog refresh's own `dataSync` foreground service (06 §5.3, CAT-07 AC1): it keeps the
 * process running while a refresh runs, so the phone may be locked, and shows the progress with
 * *Abbrechen* on the `catalog` channel. It has no refresh logic: the refresh runs in the app's
 * scope ([AppCatalogRefreshRunner]); the service follows its state and stops itself when the
 * refresh has ended, leaving a result notification.
 *
 * **Separate from the timer (rule 8, CAT-07 AC8):** another service, channel, notification IDs
 * and wake lock than `BrewTimerService`; no alarms. Nothing here may crash the process the timer
 * runs in:
 * - every start calls `startForeground` before anything else, because a service started with
 *   `startForegroundService` that stops without it crashes the app;
 * - only the latest start stops it (`stopSelfResult`), so a start queued meanwhile is never cut off;
 * - a refused foreground start (the `dataSync` time budget of Android 15+ used up) is logged, and
 *   the refresh goes on while the app lives;
 * - at the end of the time budget ([onTimeout]) the refresh is cancelled and the service stops at once;
 * - the progress is posted at most every [PROGRESS_INTERVAL_MS]: Android drops an app's updates
 *   beyond five a second, and the timer's Live Update must never be one of them;
 * - should following the refresh fail, the service stops instead of the process.
 *
 * A partial wake lock of its own keeps the CPU running while the refresh makes progress; it
 * expires [WAKE_LOCK_MS] after the last progress, so a stuck refresh never drains the battery.
 */
class CatalogRefreshService : Service() {
    /** Following the refresh fails (it should not): the service stops, the refresh and the process go on. */
    private val failure = CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "Following the refresh failed; the service stops", e)
        refresh?.log("catalog refresh: service failed (${e.javaClass.simpleName}), stopped; the refresh goes on")
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(latestStartId)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + failure)
    private var following: Job? = null
    private var latestStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null

    /** The progress the notification shows now. */
    private var shown: CatalogRefreshProgress? = null

    private val refresh: CatalogRefreshServicePort?
        get() = (application as? CatalogRefreshHost)?.catalogRefresh

    override fun onCreate() {
        super.onCreate()
        // A foreground notification on a channel that does not exist crashes the app.
        CatalogNotifications.ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val refresh = refresh
        when (intent?.action) {
            ACTION_RUN -> run(refresh, startId)

            ACTION_CANCEL -> {
                refresh?.cancel()
                // A started service that follows no refresh has nothing to wait for.
                if (following?.isActive != true) stopSelfResult(startId)
            }

            else -> if (following?.isActive != true) stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun run(refresh: CatalogRefreshServicePort?, startId: Int) {
        val state = refresh?.state?.value
        // First, whatever follows (see the class comment).
        val progress = (state as? CatalogRefreshState.Running)?.progress ?: shown ?: CatalogRefreshProgress.STARTING
        val refusal = enterForeground(progress)
        when {
            refusal != null -> {
                refresh?.log(
                    "catalog refresh: foreground refused (${refusal.javaClass.simpleName}), runs while the app lives",
                )
                stopSelfResult(startId)
            }

            refresh == null -> {
                Log.w(TAG, "No refresh to follow: the app does not hand it over")
                leave(end = null)
            }

            following?.isActive != true -> {
                refresh.log("catalog refresh: service in the foreground")
                holdWakeLock()
                following = scope.launch { follow(refresh) }
            }
        }
    }

    /** `startForeground` with [progress]; returns the refusal, or null. */
    private fun enterForeground(progress: CatalogRefreshProgress): Exception? = try {
        ServiceCompat.startForeground(
            this,
            CatalogNotifications.ID_PROGRESS,
            CatalogNotifications.progress(this, progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        shown = progress
        null
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException and the other ServiceStartNotAllowedExceptions.
        e
    } catch (e: SecurityException) {
        e
    }

    /** Shows the refresh's progress until it ends, then leaves. */
    private suspend fun follow(refresh: CatalogRefreshServicePort) = coroutineScope {
        val updates = launch {
            refresh.state
                .filterIsInstance<CatalogRefreshState.Running>()
                .map { it.progress }
                .conflate()
                .collect { progress ->
                    if (progress != shown) {
                        shown = progress
                        CatalogNotifications.post(
                            this@CatalogRefreshService,
                            CatalogNotifications.ID_PROGRESS,
                            CatalogNotifications.progress(this@CatalogRefreshService, progress),
                        )
                        holdWakeLock()
                    }
                    delay(PROGRESS_INTERVAL_MS)
                }
        }
        val end = refresh.state.first { it !is CatalogRefreshState.Running }
        updates.cancel()
        leave(end)
    }

    /** Leaves the foreground and stops; tells the [end] of the refresh, unless seen or cancelled. */
    private fun leave(end: CatalogRefreshState?) {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        refresh?.log("catalog refresh: service left the foreground")
        shown = null
        val outcome = (end as? CatalogRefreshState.Finished)?.outcome
        outcome?.let { CatalogNotifications.result(this, it) }?.let {
            CatalogNotifications.post(this, CatalogNotifications.ID_RESULT, it)
        }
        // A start queued meanwhile (a new refresh) keeps the service: it enters the foreground again.
        stopSelfResult(latestStartId)
    }

    /**
     * Android 15+ ends a `dataSync` service after six hours a day; it must stop within seconds, or
     * the app crashes. The refresh is cancelled (its catalog stays as it was) and the service leaves.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val refresh = refresh
        refresh?.log("catalog refresh: dataSync time limit reached, cancelled")
        refresh?.cancel()
        following?.cancel()
        following = null
        leave(end = null)
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    /** Holds the partial wake lock for [WAKE_LOCK_MS] from now. */
    private fun holdWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
        lock?.acquire(WAKE_LOCK_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        private const val TAG = "UmamiTCatalog"
        private const val WAKE_LOCK_TAG = "UmamiT:CatalogRefresh"
        private const val ACTION_RUN = "com.umamit.core.data.catalog.action.RUN"
        private const val ACTION_CANCEL = "com.umamit.core.data.catalog.action.CANCEL"

        /** The progress notification changes at most this often (see the class comment). */
        const val PROGRESS_INTERVAL_MS = 2_000L

        /** How long the CPU stays awake after the last progress: far beyond a page's 20 s and its retries. */
        const val WAKE_LOCK_MS = 10 * 60 * 1_000L

        /** The start of a refresh, sent with `startForegroundService` (06 §5.3). */
        fun runIntent(context: Context): Intent =
            Intent(context, CatalogRefreshService::class.java).setAction(ACTION_RUN)

        /** *Abbrechen* in the progress notification. */
        fun cancelIntent(context: Context): Intent =
            Intent(context, CatalogRefreshService::class.java).setAction(ACTION_CANCEL)
    }
}
