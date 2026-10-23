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
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.controller.AndroidMonotonicClock
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.diagnostics.AccuracyReport
import com.umamit.core.timer.diagnostics.AccuracyRun
import com.umamit.core.timer.diagnostics.FireTime
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
 * For each step it reads the pre-alert's and the alert's scheduled and actual time (wall and
 * monotonic, delta in ms) from the diagnostics log (D-060) and writes the report
 * ([AccuracyReport]: CSV, Markdown, the log lines) to `files/accuracy/` of this APK, also when
 * a step fails. It fails unless p95 ≤ 250 ms and max ≤ 1 s for both, over every step.
 *
 * It takes about 55 minutes, so the regular emulator job skips it: it runs only with the
 * instrumentation argument `umamitAccuracy=true`, which `scripts/accuracy-run.sh` passes for
 * the manually dispatched `.github/workflows/accuracy.yml` (09 §4a). Optional arguments:
 * `umamitAccuracySteps` (default 50) and `umamitAccuracyInjectDelayMs`, the gate check that
 * makes every pre-alert and alert late on purpose ([InjectedDelay]).
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

    private val start = context.getString(R.string.timer_action_start)
    private val pause = context.getString(R.string.timer_action_pause)
    private val poured = context.getString(R.string.timer_action_poured)
    private val finishSession = context.getString(R.string.timer_action_finish_session)

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
        )
        val steps = mutableListOf<StepFireTimes>()
        try {
            while (steps.size < plannedSteps) runSession(min(SESSION_STEPS, plannedSteps - steps.size), steps)
        } finally {
            writeReport(AccuracyReport(run, steps))
        }
        val report = AccuracyReport(run, steps)
        assertTrue("the timer missed the accuracy target of 04 §12:\n${report.markdown()}", report.passed)
    }

    /** One session of [count] steps, started in the foreground and ended with *Sitzung beenden*. */
    private fun runSession(count: Int, steps: MutableList<StepFireTimes>) {
        wakeAndUnlock()
        TimerTestActivity.outcomeFile(context).delete()
        context.startActivity(
            TimerTestActivity.intent(
                context,
                STEP.inWholeSeconds.toInt(),
                steps = count,
                preAlertSeconds = PRE_ALERT_SECONDS,
            ),
        )
        val outcome = eventually("the session's start in the timer process", TIMEOUT) {
            TimerTestActivity.outcomeFile(context).takeIf { it.exists() }?.readText()
        }
        assertEquals("test setup: start and Start of the session", TimerTestActivity.STARTED, outcome)
        repeat(count) { index ->
            if (index > 0) tap(liveUpdateWith(start, "READY of the next step"), start)
            steps += runStep(steps.size + 1)
        }
        tap(liveUpdateWith(finishSession, "FINISHED after the last step"), finishSession)
        eventually("the session ended after Sitzung beenden", TIMEOUT) {
            Unit.takeIf { activeNotification(TimerNotifications.ID_LIVE_UPDATE) == null }
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
            newFireTimes(number)
        }
        Log.i(
            TAG,
            "step $number/$plannedSteps: pre-alert ${preAlert.deltaMs} ms (${preAlert.source}), " +
                "alert ${deadline.deltaMs} ms (${deadline.source})",
        )
        tap(alert, poured)
        eventually("the alert of step $number gone after $poured", TIMEOUT) {
            Unit.takeIf { activeNotification(TimerNotifications.ID_ALERT) == null }
        }
        return StepFireTimes(number, preAlert, deadline)
    }

    /**
     * The pre-alert and the alert of step [number]: the accepted fire times among the log
     * entries not read before; null until both are written (the log writes in the background).
     */
    private fun newFireTimes(number: Int): Pair<FireTime, FireTime>? {
        val fresh = unreadEntries()
        val fires = fresh.mapNotNull { FireTime.parse(it.message) }
        val preAlerts = fires.filter { it.kind == TimerAlarmKind.PRE_ALERT }
        val deadlines = fires.filter { it.kind == TimerAlarmKind.DEADLINE }
        if (preAlerts.isEmpty() || deadlines.isEmpty()) return null
        check(
            "step $number: one pre-alert and one alert expected, got $preAlerts and $deadlines",
            preAlerts.size == 1 && deadlines.size == 1,
        )
        consume(fresh)
        return preAlerts.single() to deadlines.single()
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

    /** Taps the action [label] of [notification], as the user does in the shade or on the lock screen. */
    private fun tap(notification: Notification, label: String) {
        val action = notification.actions?.firstOrNull { it.title.toString() == label }
        assertNotNull("no action $label in ${notification.actions?.map { it.title }}", action)
        action!!.actionIntent.send()
    }

    /** No timer from an earlier run: alarms cancelled, screens closed, process killed, database deleted. */
    private fun resetTimer() {
        val clocks = TimerClocks(AndroidMonotonicClock(context), SystemWallClock)
        val alarms = AndroidTimerAlarms(context, clocks, TimerDiagnosticsPort {})
        alarms.reconcile(DueTimes.NONE)
        activityManager.appTasks.forEach { it.finishAndRemoveTask() }
        eventually("timer process gone", TIMEOUT) {
            timerPid()?.let(Process::killProcess)
            Unit.takeIf { timerPid() == null }
        }
        alarms.reconcile(DueTimes.NONE)
        context.deleteDatabase(UmamiDatabase.NAME)
        TimerTestActivity.outcomeFile(context).delete()
        notificationManager.cancelAll()
    }

    private fun timerPid(): Int? = activityManager.runningAppProcesses
        ?.firstOrNull { it.processName == packageName }
        ?.pid

    private fun activeNotification(id: Int): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun wakeAndUnlock() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
    }

    /** Fails with [message] and the device's state unless [condition]. */
    private fun check(message: String, condition: Boolean) {
        if (!condition) throw AssertionError("$message\n${deviceState()}")
    }

    /** Polls [probe] until it returns non-null or [timeout] passes; then fails with [what] and the device state. */
    private fun <T : Any> eventually(what: String, timeout: Duration, probe: () -> T?): T {
        val begin = TimeSource.Monotonic.markNow()
        while (begin.elapsedNow() < timeout) {
            probe()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        probe()?.let { return it }
        throw AssertionError("Timed out after $timeout waiting for: $what\n${deviceState()}")
    }

    private fun deviceState(): String = buildString {
        appendLine("SDK=${Build.VERSION.SDK_INT} timerPid=${timerPid()} interactive=${power.isInteractive}")
        appendLine("canUseFullScreenIntent=${notificationManager.canUseFullScreenIntent()}")
        appendLine("--- notifications ---")
        notificationManager.activeNotifications.forEach {
            val notification = it.notification
            appendLine(
                "id=${it.id} channel=${notification.channelId} flags=0x${Integer.toHexString(notification.flags)} " +
                    "text=${notification.extras.getCharSequence(Notification.EXTRA_TEXT)} " +
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

        /** Where the report goes: `files/accuracy/` of the test APK (read with `run-as`). */
        fun reportDirectory(context: android.content.Context): File = File(context.filesDir, "accuracy")

        private const val DEFAULT_STEPS = 50
        private const val MAX_STEPS = 200

        /** Steps per session: a realistic session length; 50 steps are five sessions. */
        private const val SESSION_STEPS = 10
        private val STEP = 60.seconds
        private const val PRE_ALERT_SECONDS = 10
        private val TIMEOUT = 10.seconds
        private val SCREEN_TIMEOUT = 5.seconds

        /** Margin after the step for the alarm, the alert and its notification on a slow CI emulator. */
        private val ALERT_TIMEOUT = 45.seconds
        private const val POLL_INTERVAL_MS = 250L
        private const val LOG_LINES_ON_FAILURE = 30
    }
}
