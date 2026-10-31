package com.umamit.core.timer

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umamit.core.common.SystemWallClock
import com.umamit.core.data.diagnostics.DiagnosticsEntry
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.controller.AndroidMonotonicClock
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.diagnostics.AccuracyReport
import com.umamit.core.timer.diagnostics.AccuracyRun
import com.umamit.core.timer.diagnostics.CueTimes
import com.umamit.core.timer.diagnostics.FireTime
import com.umamit.core.timer.diagnostics.QuickTimerAlongside
import com.umamit.core.timer.diagnostics.StepFireTimes
import com.umamit.core.timer.engine.DueTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The accuracy run of 04 §12 (TMR-30; #52, spec #40): 50 consecutive steps of 60 s with a 10 s
 * pre-alert through the real controller, alarms, foreground service and notifications of the
 * test APK (see [TimerTestApplication]), with the screen off during every step. The steps run
 * in sessions of [SESSION_STEPS]; between steps the test taps *Abgegossen* in the alert and
 * *Start* in the Live Update, as the PO does on the lock screen.
 *
 * For each step it reads the pre-alert's and the alert's scheduled time, the time the controller
 * handled the event and the time the player started the sound (wall and monotonic, delta in ms)
 * from the diagnostics log (D-060, D-068) and writes the report
 * ([AccuracyReport]: CSV, Markdown, the log lines) to `files/accuracy/` of this APK, also when
 * a step fails. It fails unless the sound started with p95 ≤ 250 ms and max ≤ 1 s for both, over
 * every step.
 *
 * **Variant `alongside`** (D-098, #137; 09 §4a): the same brew steps with a quick timer running beside
 * them, restarted every step. With each step a quick timer of [QUICK_STEP] with a pre-alert of
 * [QUICK_PRE_ALERT_SECONDS] starts (*Schnelltimer beenden und neu starten*, as Start's *Schnelltimer*
 * does), so its pre-alert and alert come with the screen off while the step runs, before the step's own
 * cues; its alert is acknowledged with *Fertig* and a quick timer of [ACROSS_STEP] without a pre-alert
 * starts, which runs across the step's pre-alert and alert and is replaced by the next step's. Never two
 * sounds are due at once, so no cue waits for the other timer's (04 §9.3): each timer's pre-alerts and
 * alerts are measured against the same target, and the report lists both. The standard variant runs as
 * it did before v0.3.0, so its results compare with the earlier runs.
 *
 * It takes about 55 minutes (`alongside` about an hour), so the regular emulator job skips it: it runs only
 * with the instrumentation argument `umamitAccuracy=true`, which `scripts/accuracy-run.sh` passes for
 * the manually dispatched `.github/workflows/accuracy.yml` (09 §4a). Optional arguments:
 * `umamitAccuracySteps` (default 50), `umamitAccuracyVariant` (`standard`, the default, or `alongside`)
 * and `umamitAccuracyInjectDelayMs`, the gate check that makes every pre-alert and alert late on purpose
 * ([InjectedDelay]), the quick timer's too.
 */
@RunWith(AndroidJUnit4::class)
class AccuracyRunTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    private val arguments = InstrumentationRegistry.getArguments()
    private val enabled = arguments.getString(ARG_ENABLED) == "true"
    private val plannedSteps = arguments.getString(ARG_STEPS)?.toIntOrNull() ?: DEFAULT_STEPS
    private val injectedDelayMs = arguments.getString(ARG_INJECT_DELAY_MS)?.toLongOrNull() ?: 0L
    private val variant = arguments.getString(ARG_VARIANT) ?: VARIANT_STANDARD

    /** The variant `alongside`: a quick timer beside every step (D-098). */
    private val alongside = variant == VARIANT_ALONGSIDE

    private val start = context.getString(R.string.timer_action_start)
    private val pause = context.getString(R.string.timer_action_pause)
    private val poured = context.getString(R.string.timer_action_poured)
    private val quickDone = context.getString(R.string.timer_action_quick_done)
    private val finishSession = context.getString(R.string.timer_action_finish_session)
    private val quickTitle = context.getString(R.string.timer_quick_title)

    private val logDirectory = File(context.noBackupFilesDir, DiagnosticsLog.DIRECTORY)

    /** Reads the log the timer process writes; this process never appends to it. */
    private val log by lazy { DiagnosticsLog(logDirectory, DiagnosticsLog.MAX_BYTES, Dispatchers.IO) }

    /** The log entries read so far, oldest first, and their keys. */
    private val logLines = mutableListOf<DiagnosticsEntry>()
    private val seen = HashSet<String>()

    @Before
    fun setUp() {
        assumeTrue("the accuracy run needs the instrumentation argument $ARG_ENABLED=true (accuracy.yml)", enabled)
        assertNotEquals(
            "test setup: the runner must not run in the timer's process (targetProcesses in the androidTest manifest)",
            packageName,
            Application.getProcessName(),
        )
        assertTrue("test setup: $ARG_STEPS must be 1..$MAX_STEPS, was $plannedSteps", plannedSteps in 1..MAX_STEPS)
        assertTrue(
            "test setup: $ARG_VARIANT must be $VARIANT_STANDARD or $VARIANT_ALONGSIDE, was $variant",
            variant == VARIANT_STANDARD || variant == VARIANT_ALONGSIDE,
        )
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set $packageName USE_FULL_SCREEN_INTENT allow")
        resetTimer()
        // The timer process is dead now: start with an empty log, report and delay setting.
        logDirectory.deleteRecursively()
        reportDirectory(context).deleteRecursively()
        if (injectedDelayMs > 0) {
            InjectedDelay.file(context).writeText(injectedDelayMs.toString())
        } else {
            InjectedDelay.file(context).delete()
        }
        wakeAndUnlock()
    }

    @After
    fun tearDown() {
        if (!enabled) return
        resetTimer()
        InjectedDelay.file(context).delete()
        wakeAndUnlock()
    }

    @Test
    fun consecutiveStepsWithTheScreenOffAlertOnTime() {
        val run = AccuracyRun(
            apiLevel = Build.VERSION.SDK_INT,
            plannedSteps = plannedSteps,
            stepSeconds = STEP.inWholeSeconds.toInt(),
            preAlertSeconds = PRE_ALERT_SECONDS,
            injectedDelayMs = injectedDelayMs,
            alongside = QuickTimerAlongside(
                seconds = QUICK_STEP.inWholeSeconds.toInt(),
                preAlertSeconds = QUICK_PRE_ALERT_SECONDS,
                acrossSeconds = ACROSS_STEP.inWholeSeconds.toInt(),
            ).takeIf { alongside },
        )
        val steps = mutableListOf<StepFireTimes>()
        val quickSteps = mutableListOf<StepFireTimes>()
        try {
            while (steps.size < plannedSteps) {
                runSession(min(SESSION_STEPS, plannedSteps - steps.size), steps, quickSteps)
            }
        } finally {
            writeReport(AccuracyReport(run, steps, quickSteps))
        }
        val report = AccuracyReport(run, steps, quickSteps)
        assertTrue("the timer missed the accuracy target of 04 §12:\n${report.markdown()}", report.passed)
    }

    /**
     * One session of [count] steps, started in the foreground and ended with *Sitzung beenden*; in the variant
     * `alongside` each step with its quick timer, the first one replacing the quick timer an earlier session left
     * running.
     */
    private fun runSession(count: Int, steps: MutableList<StepFireTimes>, quickSteps: MutableList<StepFireTimes>) {
        wakeAndUnlock()
        TimerTestActivity.outcomeFile(context).delete()
        var stepStarted = TimeSource.Monotonic.markNow()
        context.startActivity(
            if (alongside) {
                TimerTestActivity.intent(
                    context,
                    STEP.inWholeSeconds.toInt(),
                    steps = count,
                    preAlertSeconds = PRE_ALERT_SECONDS,
                    quickSeconds = QUICK_STEP.inWholeSeconds.toInt(),
                    quickPreAlertSeconds = QUICK_PRE_ALERT_SECONDS,
                    quickRestart = true,
                )
            } else {
                TimerTestActivity.intent(
                    context,
                    STEP.inWholeSeconds.toInt(),
                    steps = count,
                    preAlertSeconds = PRE_ALERT_SECONDS,
                )
            },
        )
        val outcome = eventually("the session's start in the timer process", TIMEOUT) {
            TimerTestActivity.outcomeFile(context).takeIf { it.exists() }?.readText()
        }
        assertEquals("test setup: start and Start of the session", TimerTestActivity.STARTED, outcome)
        repeat(count) { index ->
            if (alongside) {
                if (index > 0) {
                    stepStarted = TimeSource.Monotonic.markNow()
                    tap(brewNotificationWith(start, "READY of the next step"), start)
                    startQuickTimer(QUICK_STEP, QUICK_PRE_ALERT_SECONDS, "the quick timer of step ${steps.size + 1}")
                }
                runStepAlongside(steps.size + 1, stepStarted, steps, quickSteps)
            } else {
                if (index > 0) tap(liveUpdateWith(start, "READY of the next step"), start)
                steps += runStep(steps.size + 1)
            }
        }
        if (alongside) {
            tap(brewNotificationWith(finishSession, "FINISHED after the last step"), finishSession)
            eventually("the session ended after Sitzung beenden", TIMEOUT) {
                Unit.takeIf { brewNotification() == null }
            }
        } else {
            tap(liveUpdateWith(finishSession, "FINISHED after the last step"), finishSession)
            eventually("the session ended after Sitzung beenden", TIMEOUT) {
                Unit.takeIf { activeNotification(TimerNotifications.ID_LIVE_UPDATE) == null }
            }
        }
    }

    /** One running step: the screen off until the alert, its fire times read, then *Abgegossen*. */
    private fun runStep(number: Int): StepFireTimes {
        liveUpdateWith(pause, "step $number running")
        shell("input keyevent KEYCODE_SLEEP")
        eventually("the screen off in step $number", SCREEN_TIMEOUT) { Unit.takeIf { !power.isInteractive } }
        val alert = eventually("the alert (ID 2) of step $number", STEP + ALERT_TIMEOUT) {
            activeNotification(TimerNotifications.ID_ALERT)
        }
        val (preAlert, deadline) = eventually("step $number's pre-alert and alert in the diagnostics log", TIMEOUT) {
            newCues(number, TimerSlot.BREW)
        }
        // With where each start spent the time (queue, settings, outputs, sound loaded or not; D-092).
        Log.i(TAG, "step $number/$plannedSteps: pre-alert ${preAlert.summary()}, alert ${deadline.summary()}")
        tap(alert, poured)
        eventually("the alert of step $number gone after $poured", TIMEOUT) {
            Unit.takeIf { activeNotification(TimerNotifications.ID_ALERT) == null }
        }
        return StepFireTimes(number, preAlert, deadline)
    }

    /**
     * One running step [number] of the variant `alongside`, which started at [stepStarted] (at the latest) with
     * its quick timer beside it: the screen off until the quick timer's alert, its fire times read, *Fertig*, the
     * quick timer across the step's own cues started, the screen off again until the step's alert, its fire times
     * read, *Abgegossen*. Each timer's cues go to [steps] and [quickSteps] as soon as they are read.
     */
    private fun runStepAlongside(
        number: Int,
        stepStarted: TimeSource.Monotonic.ValueTimeMark,
        steps: MutableList<StepFireTimes>,
        quickSteps: MutableList<StepFireTimes>,
    ) {
        brewNotificationWith(pause, "step $number running")
        quickNotificationWith(pause, "the quick timer of step $number running")
        screenOff("in step $number")
        val quickAlert = eventually("the quick timer's alert (ID 2) in step $number", QUICK_STEP + ALERT_TIMEOUT) {
            alertOf(TimerSlot.QUICK)
        }
        val (quickPreAlert, quickDeadline) =
            eventually("the quick timer's pre-alert and alert of step $number in the diagnostics log", TIMEOUT) {
                newCues(number, TimerSlot.QUICK)
            }
        Log.i(
            TAG,
            "step $number/$plannedSteps: quick timer pre-alert ${quickPreAlert.summary()}, " +
                "alert ${quickDeadline.summary()}",
        )
        quickSteps += StepFireTimes(number, quickPreAlert, quickDeadline)
        tap(quickAlert, quickDone)
        eventually("the quick timer's alert of step $number gone after $quickDone", TIMEOUT) {
            Unit.takeIf { activeNotification(TimerNotifications.ID_ALERT) == null }
        }
        startQuickTimer(ACROSS_STEP, preAlertSeconds = 0, "the quick timer across step $number's own cues")
        screenOff("again in step $number")
        // The step's pre-alert must not meet the quick timer's sound or a screen still on (04 §9.3).
        check(
            "test setup: too slow, ${stepStarted.elapsedNow()} of step $number passed before the screen went off " +
                "again; its pre-alert comes ${STEP - PRE_ALERT_SECONDS.seconds} after its start",
            stepStarted.elapsedNow() < STEP - PRE_ALERT_SECONDS.seconds - SOUND_MARGIN,
        )
        val alert = eventually("the alert (ID 2) of step $number", STEP + ALERT_TIMEOUT) { alertOf(TimerSlot.BREW) }
        val (preAlert, deadline) = eventually("step $number's pre-alert and alert in the diagnostics log", TIMEOUT) {
            newCues(number, TimerSlot.BREW)
        }
        Log.i(TAG, "step $number/$plannedSteps: pre-alert ${preAlert.summary()}, alert ${deadline.summary()}")
        steps += StepFireTimes(number, preAlert, deadline)
        tap(alert, poured)
        eventually("the alert of step $number gone after $poured", TIMEOUT) {
            Unit.takeIf { activeNotification(TimerNotifications.ID_ALERT) == null }
        }
    }

    /**
     * A quick timer of [duration] with the *Vorwarnung* [preAlertSeconds] (0: *Aus*) beside the running brew, in
     * place of the active one, started in the foreground as Start's *Schnelltimer* does ([what] for a failure).
     */
    private fun startQuickTimer(duration: Duration, preAlertSeconds: Int, what: String) {
        wakeAndUnlock()
        TimerTestActivity.outcomeFile(context).delete()
        context.startActivity(
            TimerTestActivity.quickIntent(context, duration.inWholeSeconds.toInt(), preAlertSeconds, restart = true),
        )
        val outcome = eventually("$what: its start in the timer process", TIMEOUT) {
            TimerTestActivity.outcomeFile(context).takeIf { it.exists() }?.readText()
        }
        assertEquals("test setup: $what", TimerTestActivity.STARTED, outcome)
    }

    private fun screenOff(what: String) {
        shell("input keyevent KEYCODE_SLEEP")
        eventually("the screen off $what", SCREEN_TIMEOUT) { Unit.takeIf { !power.isInteractive } }
    }

    /**
     * The pre-alert and the alert of [slot]'s timer in step [number] among the log entries not read before:
     * each as the accepted event and the start of its sound (D-068); null until all four are written (the log
     * writes in the background).
     */
    private fun newCues(number: Int, slot: TimerSlot): Pair<CueTimes, CueTimes>? {
        val fresh = unreadEntries()
        val handled = fresh.mapNotNull { FireTime.parse(it.message) }.filter { it.slot == slot }
        val played = fresh.mapNotNull { FireTime.parsePlayed(it.message) }.filter { it.slot == slot }
        val preAlert = cue(number, TimerAlarmKind.PRE_ALERT, handled, played)
        val alert = cue(number, TimerAlarmKind.DEADLINE, handled, played)
        if (preAlert == null || alert == null) return null
        consume(fresh)
        return preAlert to alert
    }

    /** The cue of [kind] in step [number]: its one accepted event and its one sound start; null until both are in. */
    private fun cue(number: Int, kind: TimerAlarmKind, handled: List<FireTime>, played: List<FireTime>): CueTimes? {
        val events = handled.filter { it.kind == kind }
        val sounds = played.filter { it.kind == kind }
        if (events.isEmpty() || sounds.isEmpty()) return null
        check(
            "step $number: one $kind event and one sound expected, got $events and $sounds",
            events.size == 1 && sounds.size == 1,
        )
        check(
            "step $number: the $kind sound ${sounds.single()} is not that of ${events.single()}",
            sounds.single().scheduledMonoMs == events.single().scheduledMonoMs,
        )
        return CueTimes(events.single(), sounds.single())
    }

    /** The log entries not read before, oldest first. */
    private fun unreadEntries(): List<DiagnosticsEntry> =
        runBlocking { log.entries() }.asReversed().filter { key(it) !in seen }

    private fun consume(entries: List<DiagnosticsEntry>) {
        entries.forEach { if (seen.add(key(it))) logLines += it }
    }

    private fun key(entry: DiagnosticsEntry) = "${entry.time}\t${entry.message}"

    /** The report files of [report] (also when a step failed) and the log lines they come from. */
    private fun writeReport(report: AccuracyReport) {
        consume(unreadEntries())
        val directory = reportDirectory(context).apply { mkdirs() }
        File(directory, "accuracy.csv").writeText(report.csv())
        File(directory, "accuracy.md").writeText(report.markdown())
        File(directory, "diagnostics.txt").writeText(logLines.joinToString("\n", postfix = "\n") { it.line })
        Log.i(TAG, "report in $directory:\n${report.markdown()}")
    }

    /** Waits for the Live Update (ID 1) with the action [label], in the state [what]. */
    private fun liveUpdateWith(label: String, what: String): Notification =
        eventually("$what: the Live Update with $label", TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)
                ?.takeIf { live -> live.actions.orEmpty().any { it.title.toString() == label } }
        }

    /**
     * Waits for the brew's notification with the action [label], in the state [what]: with a quick timer beside
     * it, the Live Update (ID 1) while it ends next, else the other timer's notification (ID 5; D-098).
     */
    private fun brewNotificationWith(label: String, what: String): Notification =
        eventually("$what: the brew's notification (ID 1 or 5) with $label", TIMEOUT) {
            brewNotification()?.takeIf { it.hasAction(label) }
        }

    /** Waits for the quick timer's notification (ID 1 or 5) with the action [label], in the state [what]. */
    private fun quickNotificationWith(label: String, what: String): Notification =
        eventually("$what: the quick timer's notification (ID 1 or 5) with $label", TIMEOUT) {
            timerNotifications().firstOrNull { it.title()?.startsWith(quickTitle) == true && it.hasAction(label) }
        }

    /** The brew's Live Update or other-timer notification; null when it shows none of them. */
    private fun brewNotification(): Notification? =
        timerNotifications().firstOrNull { it.title()?.startsWith(TimerTestActivity.TEA) == true }

    /** The notifications of a timer that does not alert: the Live Update (ID 1) and the other timer's (ID 5). */
    private fun timerNotifications(): List<Notification> =
        listOf(TimerNotifications.ID_LIVE_UPDATE, TimerNotifications.ID_OTHER).mapNotNull(::activeNotification)

    /** The alert (ID 2) when it is [slot]'s: „Schnelltimer" for the quick timer, „Testtee · Aufguss N" for the brew. */
    private fun alertOf(slot: TimerSlot): Notification? = activeNotification(TimerNotifications.ID_ALERT)?.takeIf {
        when (slot) {
            TimerSlot.QUICK -> it.text() == quickTitle
            TimerSlot.BREW -> it.text()?.startsWith(TimerTestActivity.TEA) == true
        }
    }

    private fun Notification.hasAction(label: String) = actions.orEmpty().any { it.title.toString() == label }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    /** Taps the action [label] of [notification], as the user does in the shade or on the lock screen. */
    private fun tap(notification: Notification, label: String) {
        val action = notification.actions?.firstOrNull { it.title.toString() == label }
        assertNotNull("no action $label in ${notification.actions?.map { it.title }}", action)
        action!!.actionIntent.send()
    }

    /**
     * No timer from an earlier run, as in [ProcessDeathTest]: alarms and the alert watchdog
     * cancelled, screens closed, process killed, database deleted. A process an alarm started
     * meanwhile recovered the old session, so it is killed once more after the database is gone.
     */
    private fun resetTimer() {
        val clocks = TimerClocks(AndroidMonotonicClock(context), SystemWallClock)
        val alarms = AndroidTimerAlarms(context, clocks, TimerDiagnosticsPort {})
        TimerSlot.entries.forEach { alarms.reconcile(it, DueTimes.NONE) }
        TimerSlot.entries.forEach { alarms.watchAlert(it, null) }
        activityManager.appTasks.forEach { it.finishAndRemoveTask() }
        killTimerProcess()
        TimerSlot.entries.forEach { alarms.reconcile(it, DueTimes.NONE) }
        TimerSlot.entries.forEach { alarms.watchAlert(it, null) }
        context.deleteDatabase(UmamiDatabase.NAME)
        killTimerProcess()
        TimerSlot.entries.forEach { alarms.reconcile(it, DueTimes.NONE) }
        TimerSlot.entries.forEach { alarms.watchAlert(it, null) }
        TimerTestActivity.outcomeFile(context).delete()
        notificationManager.cancelAll()
    }

    private fun killTimerProcess() = eventually("timer process gone", TIMEOUT) {
        timerPid()?.let(Process::killProcess)
        Unit.takeIf { timerPid() == null }
    }

    private fun timerPid(): Int? = activityManager.runningAppProcesses
        ?.firstOrNull { it.processName == packageName }
        ?.pid

    private fun activeNotification(id: Int): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun wakeAndUnlock() = UnlockedDevice.unlock()

    /** Fails with [message] and the device's state unless [condition]. */
    private fun check(message: String, condition: Boolean) {
        if (!condition) throw AssertionError("$message\n${deviceState()}")
    }

    /** Polls [poll] until it returns non-null or [timeout] passes; then fails with [what] and the device state. */
    private fun <T : Any> eventually(what: String, timeout: Duration, poll: () -> T?): T {
        val begin = TimeSource.Monotonic.markNow()
        while (begin.elapsedNow() < timeout) {
            poll()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        poll()?.let { return it }
        throw AssertionError("Timed out after $timeout waiting for: $what\n${deviceState()}")
    }

    private fun deviceState(): String = buildString {
        appendLine(
            "SDK=${Build.VERSION.SDK_INT} variant=$variant timerPid=${timerPid()} interactive=${power.isInteractive}",
        )
        appendLine("canUseFullScreenIntent=${notificationManager.canUseFullScreenIntent()}")
        appendLine("--- notifications ---")
        notificationManager.activeNotifications.forEach {
            val notification = it.notification
            appendLine(
                "id=${it.id} channel=${notification.channelId} flags=0x${Integer.toHexString(notification.flags)} " +
                    "title=${notification.title()} text=${notification.text()} " +
                    "actions=${notification.actions?.map { action -> action.title }}",
            )
        }
        appendLine("--- last log entries ---")
        runBlocking { log.entries() }.take(LOG_LINES_ON_FAILURE).asReversed().forEach { appendLine(it.line) }
    }.also { Log.e(TAG, it) }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    companion object {
        private const val TAG = "AccuracyRun"

        /** `true` runs the test; any other value skips it (the regular emulator job). */
        const val ARG_ENABLED = "umamitAccuracy"
        const val ARG_STEPS = "umamitAccuracySteps"
        const val ARG_INJECT_DELAY_MS = "umamitAccuracyInjectDelayMs"

        /** `standard` (the default) or `alongside` (D-098, #137). */
        const val ARG_VARIANT = "umamitAccuracyVariant"
        const val VARIANT_STANDARD = "standard"
        const val VARIANT_ALONGSIDE = "alongside"

        /** Where the report goes: `files/accuracy/` of the test APK (read with `run-as`). */
        fun reportDirectory(context: android.content.Context): File = File(context.filesDir, "accuracy")

        private const val DEFAULT_STEPS = 50
        private const val MAX_STEPS = 200

        /** Steps per session: a realistic session length; 50 steps are five sessions. */
        private const val SESSION_STEPS = 10
        private val STEP = 60.seconds
        private const val PRE_ALERT_SECONDS = 10

        /**
         * `alongside`: the quick timer started with each step, ending with the screen off well before the step's
         * pre-alert (at 50 s), and its *Vorwarnung*.
         */
        private val QUICK_STEP = 25.seconds
        private const val QUICK_PRE_ALERT_SECONDS = 10

        /**
         * `alongside`: the quick timer across the step's pre-alert and alert, without a pre-alert; the next step's
         * replaces it long before it ends.
         */
        private val ACROSS_STEP = 300.seconds

        /** `alongside`: the quick timer's alert is done and the screen off this long before the step's pre-alert. */
        private val SOUND_MARGIN = 5.seconds
        private val TIMEOUT = 10.seconds
        private val SCREEN_TIMEOUT = 5.seconds

        /** Margin after the step for the alarm, the alert and its notification on a slow CI emulator. */
        private val ALERT_TIMEOUT = 45.seconds
        private const val POLL_INTERVAL_MS = 250L
        private const val LOG_LINES_ON_FAILURE = 30
    }
}
