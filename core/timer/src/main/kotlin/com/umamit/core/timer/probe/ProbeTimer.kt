package com.umamit.core.timer.probe

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.umamit.core.timer.ExactAlarmScheduler
import com.umamit.core.timer.TimerAlarm
import com.umamit.core.timer.TimerNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** State of the probe step for the probe screen. */
sealed interface ProbeStatus {
    data object Idle : ProbeStatus

    /** @property exactAlarms false when the alarms fell back to inexact ones (should not happen). */
    data class Active(val step: ProbeStep, val phase: ProbePhase, val exactAlarms: Boolean) : ProbeStatus
}

/**
 * Probe controller (M1-03a, #15): one tap starts a test step that uses the
 * platform mechanisms of the real timer (04 §6, §8):
 * - exact alarms for the pre-alert and the deadline ([ExactAlarmScheduler]);
 * - the `specialUse` foreground service with the Live Update ([ProbeTimerService]);
 * - at the deadline, the alert notification with its full-screen intent
 *   ([ProbeAlertActivity]).
 *
 * The transitions come only from the exact alarms, so the probe proves their
 * delivery; the service refreshes the Live Update every 15 s. Process-wide
 * singleton: the probe has one step at a time.
 */
object ProbeTimer {
    val DEFAULT_STEP_DURATION: Duration = 60.seconds
    val PRE_ALERT_LEAD: Duration = 10.seconds

    /**
     * After the deadline the service stays in the foreground this long so the
     * alert can play (04 §9.1, longest alert 2 min), then the step stops.
     */
    val ALERT_WINDOW: Duration = 2.minutes

    /** Alarms delivered later than this after the deadline are ignored. */
    private val STALE_AFTER: Duration = 10.minutes

    private const val TAG = "UmamiTProbe"

    /** Test seam (plan §3.1 seam 2): a shorter step for the instrumented tests; null = [DEFAULT_STEP_DURATION]. */
    @VisibleForTesting
    @Volatile
    var stepDurationOverride: Duration? = null

    private val mutableStatus = MutableStateFlow<ProbeStatus>(ProbeStatus.Idle)
    val status: StateFlow<ProbeStatus> = mutableStatus.asStateFlow()

    /** Starts a new test step, replacing a running one. Call from a user tap (foreground). */
    fun start(context: Context): ProbeStatus.Active {
        val app = context.applicationContext
        TimerNotifications.ensureChannels(app)
        TimerNotifications.cancel(app, TimerNotifications.ID_ALERT)

        val step = ProbeStep(
            startElapsedMs = SystemClock.elapsedRealtime(),
            startWallMs = System.currentTimeMillis(),
            durationMs = (stepDurationOverride ?: DEFAULT_STEP_DURATION).inWholeMilliseconds,
            preAlertLeadMs = PRE_ALERT_LEAD.inWholeMilliseconds,
        )
        val exact = scheduleAlarms(ExactAlarmScheduler(app), step)
        val active = ProbeStatus.Active(step, ProbePhase.RUNNING, exact)
        mutableStatus.value = active
        Log.i(TAG, "Step started: ${step.durationMs} ms, pre-alert at +${step.preAlertOffsetMs} ms, exact=$exact")
        ContextCompat.startForegroundService(app, ProbeTimerService.intent(app, ProbeTimerService.ACTION_START, step))
        return active
    }

    /** Stops the step: cancels the alarms, the alert and the foreground service. Idempotent. */
    fun stop(context: Context) {
        val app = context.applicationContext
        ExactAlarmScheduler(app).cancelAll()
        app.stopService(Intent(app, ProbeTimerService::class.java))
        TimerNotifications.cancel(app, TimerNotifications.ID_ALERT)
        TimerNotifications.cancel(app, TimerNotifications.ID_LIVE_UPDATE)
        mutableStatus.value = ProbeStatus.Idle
        Log.i(TAG, "Step stopped")
    }

    /** Called by [com.umamit.core.timer.ExactAlarmReceiver]; idempotent, survives process death. */
    internal fun onAlarm(context: Context, alarm: TimerAlarm, intent: Intent) {
        val step = ProbeStep.readFrom(intent)
        val scheduledAt = when (alarm) {
            TimerAlarm.PRE_ALERT -> step?.preAlertElapsedMs
            TimerAlarm.DEADLINE -> step?.deadlineElapsedMs
        }
        if (step == null || scheduledAt == null) {
            Log.w(TAG, "$alarm without a valid step ignored")
            return
        }
        val now = SystemClock.elapsedRealtime()
        // Scheduled vs actual fire time (06 §5.6 records these in the diagnostics log from M1-09).
        Log.i(TAG, "$alarm fired: scheduled=$scheduledAt actual=$now late=${now - scheduledAt} ms")
        if (now - step.deadlineElapsedMs > STALE_AFTER.inWholeMilliseconds) {
            Log.w(TAG, "$alarm ignored: more than $STALE_AFTER after the deadline")
        } else {
            deliver(context.applicationContext, alarm, step)
        }
    }

    private fun deliver(app: Context, alarm: TimerAlarm, step: ProbeStep) {
        val phase = if (alarm == TimerAlarm.DEADLINE) ProbePhase.FINISHED else ProbePhase.PRE_ALERTED
        advance(step, phase)
        val action = when (alarm) {
            TimerAlarm.PRE_ALERT -> ProbeTimerService.ACTION_PRE_ALERT
            TimerAlarm.DEADLINE -> ProbeTimerService.ACTION_DEADLINE
        }
        try {
            // Exact-alarm delivery allows starting the FGS from the background (04 §6.2).
            ContextCompat.startForegroundService(app, ProbeTimerService.intent(app, action, step))
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "FGS start refused at $alarm; posting the alert without the service", e)
            if (alarm == TimerAlarm.DEADLINE) {
                TimerNotifications.ensureChannels(app)
                // The alert must be the only alerting notification (TimerNotifications.ID_ALERT).
                TimerNotifications.cancel(app, TimerNotifications.ID_LIVE_UPDATE)
                TimerNotifications.post(app, TimerNotifications.ID_ALERT, ProbeNotifications.alert(app))
            }
        }
    }

    /** Moves the status forward to [phase] for [step] (never backwards; adopts the step after process death). */
    internal fun advance(step: ProbeStep, phase: ProbePhase) {
        mutableStatus.update { current ->
            when {
                current is ProbeStatus.Active && current.step == step ->
                    if (phase > current.phase) current.copy(phase = phase) else current

                current is ProbeStatus.Active -> current

                // a newer step replaced this one

                else -> ProbeStatus.Active(step, phase, exactAlarms = true)
            }
        }
    }

    /** Called by the service when it is destroyed without [stop] (e.g. after the alert window). */
    internal fun onServiceFinished(step: ProbeStep?) {
        mutableStatus.update { current ->
            if (current is ProbeStatus.Active && (step == null || current.step == step)) ProbeStatus.Idle else current
        }
    }

    private fun scheduleAlarms(scheduler: ExactAlarmScheduler, step: ProbeStep): Boolean {
        scheduler.cancelAll()
        val preAlertAt = step.preAlertElapsedMs
        val preAlertExact = preAlertAt == null ||
            scheduler.schedule(TimerAlarm.PRE_ALERT, preAlertAt) { step.writeTo(it) }
        val deadlineExact = scheduler.schedule(TimerAlarm.DEADLINE, step.deadlineElapsedMs) { step.writeTo(it) }
        return preAlertExact && deadlineExact
    }
}
