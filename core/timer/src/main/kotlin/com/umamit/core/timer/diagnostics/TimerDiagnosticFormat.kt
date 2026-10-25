package com.umamit.core.timer.diagnostics

import com.umamit.core.common.ClockReading
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.alert.AlertOutputs
import com.umamit.core.timer.controller.AlarmClock
import com.umamit.core.timer.controller.ScheduledTime
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * The one-line text of a [TimerDiagnostic] in the diagnostics log (06 §5.6,
 * D-060). English and technical, so the PO can paste it into an issue and the
 * accuracy workflow can read it (04 §12): a time-based event shows
 *
 * `PRE_ALERT scheduled 10:00:50.000 (mono 5050000) actual 10:00:50.080 (mono 5050080) delta +80 ms`,
 *
 * every other timer entry the monotonic time and boot it was read at. Only
 * plain values are written, never a model's `toString()`, so no note text
 * can reach the log; tea names do not appear either.
 */
object TimerDiagnosticFormat {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** The message of [entry]; the log adds the wall time of [TimerDiagnostic.at]. */
    @Suppress("CyclomaticComplexMethod") // one flat branch per entry type, checked exhaustive by the compiler
    fun message(entry: TimerDiagnostic): String = when (entry) {
        is TimerDiagnostic.EventHandled ->
            "event ${eventName(entry.event)} from ${entry.source}: " +
                "${phase(entry.from)} -> ${phase(entry.to)} ${outcome(entry.outcome)} | " +
                (entry.scheduled?.let { timing(it, entry.at) } ?: clocks(entry.at))

        is TimerDiagnostic.AlarmReceived ->
            "alarm ${entry.kind} received | " +
                (entry.scheduled?.let { timing(it, entry.at) } ?: "nothing due | ${clocks(entry.at)}")

        is TimerDiagnostic.PersistFailed ->
            "persist ${if (entry.retry) "retry " else ""}failed: ${error(entry.error)} | ${clocks(entry.at)}"

        is TimerDiagnostic.EffectFailed ->
            "effect ${entry.effect::class.simpleName} failed: ${error(entry.error)} | ${clocks(entry.at)}"

        is TimerDiagnostic.StaleAlertCompletion ->
            "alert end of step ${entry.alert.stepIndex + 1} ignored: not the playing alert | ${clocks(entry.at)}"

        is TimerDiagnostic.SnapshotUnreadable ->
            "recovery: stored timer unreadable, cleared: ${error(entry.error)} | ${clocks(entry.at)}"

        is TimerDiagnostic.RecoveryFailed ->
            "recovery: reading the stored timer failed: ${error(entry.error)} | ${clocks(entry.at)}"

        is TimerDiagnostic.RecoveryRetriesStopped ->
            "recovery: retries stopped after ${entry.failingForMs / MS_PER_SECOND} s of failed reads; " +
                "the next input reads again | ${clocks(entry.at)}"

        is TimerDiagnostic.AlarmNotExact ->
            "alarm ${entry.kind} set inexact: exact alarms refused | ${clocks(entry.at)}"

        is TimerDiagnostic.ServiceInForeground ->
            "service: in the foreground with notification ${entry.notificationId} | ${clocks(entry.at)}"

        is TimerDiagnostic.ServiceTookOverStale ->
            "service: took over the stale foreground notification ${entry.notificationId} " +
                "of a killed process to remove it | ${clocks(entry.at)}"

        is TimerDiagnostic.ServiceStopped -> "service: stopped | ${clocks(entry.at)}"

        is TimerDiagnostic.AlertPlayed ->
            "alert ${entry.cue}: ringer ${entry.conditions.ringerMode}, in call ${yesNo(entry.conditions.inCall)}, " +
                "DND blocks ${yesNo(entry.conditions.dndBlocksAlerts)} -> ${outputs(entry.outputs)} | " +
                "tone ${entry.tone}, length ${entry.length} | " +
                (entry.scheduled?.let { timing(it, entry.at) } ?: clocks(entry.at))

        is TimerDiagnostic.PermissionsAtStart -> with(entry.states) {
            "process start: notifications ${yesNo(notifications)}, " +
                "live updates ${promotedNotifications?.let(::yesNo) ?: "n/a"}, " +
                "full-screen alert ${yesNo(fullScreenIntent)}, exact alarms ${yesNo(exactAlarms)}, " +
                "battery unrestricted ${yesNo(batteryUnrestricted)} | ${clocks(entry.at)}"
        }

        is TimerDiagnostic.RecoveryRequested ->
            "recovery requested by ${entry.trigger}" +
                (if (entry.alreadyRecovered) ": already done in this process" else "") + " | ${clocks(entry.at)}"

        is TimerDiagnostic.Recovered ->
            "recovery: stored ${entry.from}" + (if (entry.rebooted) " from before a reboot" else "") +
                " -> ${phase(entry.to)}: ${entry.decision.name.lowercase().replace('_', ' ')}, " +
                (if (entry.service) "with" else "without") + " the service | ${clocks(entry.at)}"

        is TimerDiagnostic.OrphanSessionEnded ->
            "recovery: a session left in progress without its timer was " +
                (if (entry.saved) "saved" else "discarded") + " | ${clocks(entry.at)}"

        is TimerDiagnostic.OrphanSessionsFailed ->
            "recovery: ending the sessions left in progress failed: ${error(entry.error)} | ${clocks(entry.at)}"

        is TimerDiagnostic.ServiceStartRefused ->
            "service: foreground start with notification ${entry.notificationId} refused" +
                (if (entry.again) " again" else "") + ": " +
                "${error(entry.error)} | ${clocks(entry.at)}"
    }

    private fun eventName(event: TimerEvent): String = when (event) {
        is TimerEvent.StartSession -> "StartSession (${steps(event.steps.size)})"
        is TimerEvent.EditSteps -> "EditSteps (${steps(event.steps.size)})"
        is TimerEvent.StartQuickTimer -> "StartQuickTimer (${event.durationSeconds} s)"
        else -> event::class.simpleName.orEmpty()
    }

    private fun steps(count: Int) = if (count == 1) "1 step" else "$count steps"

    private fun phase(phase: TimerPhase?): String = phase?.name ?: "IDLE"

    private fun outcome(outcome: TransitionOutcome): String = when (outcome) {
        TransitionOutcome.Accepted -> "accepted"
        TransitionOutcome.Ignored -> "ignored"
        is TransitionOutcome.Rejected -> "rejected: ${outcome.reason::class.simpleName}"
    }

    /** Scheduled vs actual on both clocks; the other clock's scheduled time is derived from the delay. */
    private fun timing(scheduled: ScheduledTime, now: ClockReading): String {
        val late = scheduled.lateMs(now)
        val (scheduledWall, scheduledMono) = when (scheduled.kind.clock) {
            AlarmClock.ELAPSED -> now.wallMs - late to scheduled.atMs
            AlarmClock.WALL -> scheduled.atMs to now.elapsedMs - late
        }
        val sign = if (late < 0) "-" else "+"
        return "${scheduled.kind} scheduled ${time(scheduledWall, now)} (mono $scheduledMono) " +
            "actual ${time(now.wallMs, now)} (mono ${now.elapsedMs}) delta $sign${kotlin.math.abs(late)} ms"
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun outputs(outputs: AlertOutputs): String = when {
        outputs.sound && outputs.vibration -> "sound + vibration"
        outputs.sound -> "sound"
        outputs.vibration -> "vibration"
        else -> "visual only"
    }

    private fun clocks(now: ClockReading): String = "mono ${now.elapsedMs} boot ${now.bootCount}"

    private fun time(wallMs: Long, now: ClockReading): String =
        TIME.format(Instant.ofEpochMilli(wallMs).atZone(now.zone))

    /** The error's class and message, never its cause chain or stack. */
    private fun error(error: Throwable): String =
        listOfNotNull(error::class.simpleName, error.message?.take(MAX_ERROR_CHARS)).joinToString(": ")

    private const val MAX_ERROR_CHARS = 200
    private const val MS_PER_SECOND = 1_000L
}
