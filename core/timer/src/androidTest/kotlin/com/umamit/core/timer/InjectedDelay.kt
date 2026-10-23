package com.umamit.core.timer

import android.content.Context
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerAlarmPort
import com.umamit.core.timer.controller.TimerWakeUpPort
import com.umamit.core.timer.engine.DueTimes
import java.io.File

/**
 * The accuracy run's gate check (#52): when the runner has written a delay to [file], the test
 * app's controller sets every pre-alert and deadline alarm, and arms its in-process wake-up,
 * that many ms late, so both paths deliver late and the run must fail its target (04 §12).
 * Only the test APK has this; the app's timer is unchanged. Read when the controller is built.
 */
object InjectedDelay {
    fun file(context: Context): File = File(context.filesDir, "timer-test-inject-delay-ms.txt")

    /** The delay in ms; 0 (a real run) without the file. */
    fun read(context: Context): Long = file(context).takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0
}

/** [alarms] with the pre-alert and the deadline [delayMs] late. */
class DelayedAlarms(private val alarms: TimerAlarmPort, private val delayMs: Long) : TimerAlarmPort {
    override fun reconcile(due: DueTimes) = alarms.reconcile(
        due.copy(
            preAlertAtElapsedMs = due.preAlertAtElapsedMs?.plus(delayMs),
            deadlineElapsedMs = due.deadlineElapsedMs?.plus(delayMs),
        ),
    )

    override fun retry(kind: TimerAlarmKind, atMs: Long) =
        alarms.retry(kind, if (kind in DELAYED) atMs + delayMs else atMs)

    override fun watchAlert(atElapsedMs: Long?) = alarms.watchAlert(atElapsedMs)

    private companion object {
        val DELAYED = setOf(TimerAlarmKind.PRE_ALERT, TimerAlarmKind.DEADLINE)
    }
}

/** [wakeUp] firing [extraMs] late. */
class DelayedWakeUp(private val wakeUp: TimerWakeUpPort, private val extraMs: Long) : TimerWakeUpPort {
    override fun arm(delayMs: Long, onDue: suspend () -> Unit) = wakeUp.arm(delayMs + extraMs, onDue)

    override fun cancel() = wakeUp.cancel()
}
