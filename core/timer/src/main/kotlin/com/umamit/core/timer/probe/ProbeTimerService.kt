package com.umamit.core.timer.probe

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.umamit.core.timer.AlertPlayer
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.alert.AlertPlayers
import kotlin.time.Duration.Companion.seconds

/**
 * The probe's `specialUse` foreground service (04 §6.3, §13). It hosts the
 * Live Update (ID 1, 04 §8.3), the partial wake lock and the [AlertPlayer];
 * it has no timer logic: the transitions arrive as commands from
 * [ProbeTimer] (start) and from the exact alarms (pre-alert, deadline).
 */
class ProbeTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var step: ProbeStep? = null
    private var phase = ProbePhase.RUNNING
    private var wakeLock: PowerManager.WakeLock? = null
    private val alertPlayer: AlertPlayer by lazy { createAlertPlayer() }

    private val refresh = object : Runnable {
        override fun run() {
            postLiveUpdate()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val endOfAlertWindow = Runnable {
        Log.i(TAG, "Alert window over; stopping the step")
        ProbeTimer.stop(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandStep = ProbeStep.readFrom(intent)
        if (commandStep == null) {
            Log.w(TAG, "Command without a step: ${intent?.action}")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> onStepStarted(commandStep)
            ACTION_PRE_ALERT -> onPhaseReached(commandStep, ProbePhase.PRE_ALERTED)
            ACTION_DEADLINE -> onPhaseReached(commandStep, ProbePhase.FINISHED)
            else -> Log.w(TAG, "Unknown command ${intent?.action}")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        alertPlayer.stopAlert()
        releaseWakeLock()
        ProbeTimer.onServiceFinished(step)
        super.onDestroy()
    }

    /**
     * The ringer-mode alert (04 §9.1, #16) with the default settings (Klangschale,
     * Normal = up to 15 s, vibration on). It is called at the pre-alert
     * ([AlertPlayer.playPreAlert]), at the deadline ([AlertPlayer.startAlert])
     * and when the step stops ([AlertPlayer.stopAlert]).
     */
    private fun createAlertPlayer(): AlertPlayer = AlertPlayers.ringerMode(this)

    private fun onStepStarted(newStep: ProbeStep) {
        alertPlayer.stopAlert()
        handler.removeCallbacksAndMessages(null)
        step = newStep
        phase = newStep.phaseAt(SystemClock.elapsedRealtime())
        startInForeground()
        acquireWakeLock(newStep)
        handler.postDelayed(refresh, REFRESH_INTERVAL_MS)
    }

    private fun onPhaseReached(alarmStep: ProbeStep, reached: ProbePhase) {
        val current = step
        if (current != null && current != alarmStep) {
            // A newer step replaced this one (its alarms replaced the old ones);
            // keep the foreground promise of startForegroundService.
            Log.w(TAG, "$reached for a replaced step ignored")
            startInForeground()
            return
        }
        if (current == null) {
            // Process was restarted by the alarm: adopt the step.
            step = alarmStep
            acquireWakeLock(alarmStep)
        }
        val advanced = reached > phase
        if (advanced) phase = reached
        startInForeground()
        if (!advanced) return

        when (reached) {
            ProbePhase.PRE_ALERTED -> alertPlayer.playPreAlert()

            ProbePhase.FINISHED -> {
                handler.removeCallbacks(refresh)
                TimerNotifications.post(this, TimerNotifications.ID_ALERT, ProbeNotifications.alert(this))
                alertPlayer.startAlert()
                handler.postDelayed(endOfAlertWindow, ProbeTimer.ALERT_WINDOW.inWholeMilliseconds)
            }

            ProbePhase.RUNNING -> Unit
        }
    }

    /** startForeground on every command keeps the promise of startForegroundService and updates the Live Update. */
    private fun startInForeground() {
        val notification = liveUpdate() ?: return
        try {
            ServiceCompat.startForeground(
                this,
                TimerNotifications.ID_LIVE_UPDATE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // 04 §6.3: fall back to a plain ongoing notification.
            Log.w(TAG, "startForeground refused; showing a plain ongoing notification", e)
            TimerNotifications.post(this, TimerNotifications.ID_LIVE_UPDATE, notification)
        }
    }

    private fun postLiveUpdate() {
        liveUpdate()?.let { TimerNotifications.post(this, TimerNotifications.ID_LIVE_UPDATE, it) }
    }

    private fun liveUpdate(): Notification? {
        val current = step ?: return null
        TimerNotifications.ensureChannels(this)
        return ProbeNotifications.liveUpdate(
            context = this,
            step = current,
            phase = phase,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            promote = TimerNotifications.canPromote(this),
        )
    }

    /** Partial wake lock for the step and the alert (04 §6.3: remaining + alert length + 30 s). */
    private fun acquireWakeLock(forStep: ProbeStep) {
        releaseWakeLock()
        val timeout = forStep.remainingAt(SystemClock.elapsedRealtime()) +
            ProbeTimer.ALERT_WINDOW.inWholeMilliseconds + WAKE_LOCK_MARGIN.inWholeMilliseconds
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(timeout)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    internal companion object {
        const val ACTION_START = "com.umamit.core.timer.action.PROBE_START"
        const val ACTION_PRE_ALERT = "com.umamit.core.timer.action.PROBE_PRE_ALERT"
        const val ACTION_DEADLINE = "com.umamit.core.timer.action.PROBE_DEADLINE"

        /** 04 §8.3: the progress is refreshed every 15 s (the chip counts down by itself). */
        val REFRESH_INTERVAL_MS = 15.seconds.inWholeMilliseconds
        private val WAKE_LOCK_MARGIN = 30.seconds
        private const val WAKE_LOCK_TAG = "UmamiT:ProbeTimer"
        private const val TAG = "UmamiTProbe"

        fun intent(context: Context, action: String, step: ProbeStep): Intent =
            step.writeTo(Intent(context, ProbeTimerService::class.java).setAction(action))
    }
}
