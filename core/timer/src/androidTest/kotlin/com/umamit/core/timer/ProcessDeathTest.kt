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
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umamit.core.common.SystemWallClock
import com.umamit.core.data.diagnostics.DiagnosticsLog
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.alarm.TimerAlarmReceiver
import com.umamit.core.timer.controller.AndroidMonotonicClock
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.diagnostics.FireTime
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.service.BrewTimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * E3 in the CI emulator job (API 35/36; #42, #46, 04 §6.2, §7.2, §12; spec #40 seam 5): a short
 * step runs through the real controller, alarm adapter and foreground service in the main
 * process of this test APK; the test kills that process during the step.
 *
 * - The deadline alarm starts a new process, whose controller recovers the step and alerts: the
 *   full-screen alert shows over the lock screen, and the alert notification is the foreground
 *   notification, not silenced (D-044), and names the recovered session's tea. Notification 1
 *   is gone, also when the kill met an update of it and left it flagged for the new process
 *   (D-084); the log says when the kill came and whether it left notification 1.
 * - Or the user opens the app again before the deadline: its process start recovers the step,
 *   which shows as it was, and the alert still comes on time.
 * - With a quick timer beside the step (D-098, #137), due together with it: both come back after
 *   two kills, the brew's alert first and on time, the quick timer's held behind it until
 *   *Abgegossen*, then at once.
 *
 * The test runs in its own process (`:runner`, see the androidTest manifest), so it outlives
 * the kill. `am kill` spares a process with a foreground service (it only kills background
 * processes), so the test kills it with SIGKILL from the same uid, as the low-memory killer
 * or a vendor's task killer does (D-057).
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    private val logDirectory = File(context.noBackupFilesDir, DiagnosticsLog.DIRECTORY)

    /** Reads the log the timer process writes; this process never appends to it. */
    private val log by lazy { DiagnosticsLog(logDirectory, DiagnosticsLog.MAX_BYTES, Dispatchers.IO) }

    @Before
    fun setUp() {
        assertNotEquals(
            "test setup: the runner must not run in the timer's process (targetProcesses in the androidTest manifest)",
            packageName,
            Application.getProcessName(),
        )
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set $packageName USE_FULL_SCREEN_INTENT allow")
        resetTimer()
        wakeAndUnlock()
    }

    @After
    fun tearDown() {
        resetTimer()
        wakeAndUnlock()
    }

    @Test
    fun aStepWhoseProcessIsKilledStillAlertsFullScreenAtItsDeadline() {
        val started = TimeSource.Monotonic.markNow()
        context.startActivity(TimerTestActivity.intent(context, STEP.inWholeSeconds.toInt()))
        val outcome = eventually("the step's start in the timer process", START_TIMEOUT) {
            TimerTestActivity.outcomeFile(context).takeIf { it.exists() }?.readText()
        }
        assertEquals("test setup: start and Start of the step", TimerTestActivity.STARTED, outcome)
        val stepStartWallMs = TimerTestActivity.deadlineFile(context).readText().toLong() - STEP.inWholeMilliseconds
        eventually("foreground BrewTimerService", START_TIMEOUT) {
            serviceBlock()?.takeIf { it.contains("isForeground=true") }
        }
        eventually("the deadline alarm in dumpsys alarm", START_TIMEOUT) { deadlineAlarm() }

        val kill = killDuringStep(started, stepStartWallMs)
        val killed = kill.pid
        assertNotNull("the deadline alarm must survive the process", deadlineAlarm())

        // With the screen off the full-screen intent launches the activity (on and
        // unlocked it would be a heads-up, 04 §8.2).
        shell("input keyevent KEYCODE_SLEEP")
        // The alert first, then the screen it opens: a stand-in closed by an earlier test can
        // still be dumpsys' LastResumedActivity before the deadline.
        eventually("the alert notification (ID 2) at the deadline", STEP + ALERT_TIMEOUT) {
            activeNotification(TimerNotifications.ID_ALERT)
        }
        eventually("the timer screen shown at the deadline", ALERT_TIMEOUT) { resumedAlertActivity() }

        val restarted = timerPid()
        assertNotNull("the alarm did not start a new timer process", restarted)
        assertNotEquals("the timer process was not restarted", killed, restarted)
        val alert = activeNotification(TimerNotifications.ID_ALERT)
        assertNotNull("alert notification (ID 2) missing", alert)
        alert!!
        assertEquals(TimerNotifications.CHANNEL_STEEP_FINISHED, alert.channelId)
        assertEquals(Notification.CATEGORY_ALARM, alert.category)
        assertNotNull("the alert has no full-screen intent", alert.fullScreenIntent)
        assertTrue(
            "the alert is marked FLAG_SILENT (force-grouped), flags=0x${Integer.toHexString(alert.flags)}",
            alert.flags and FLAG_SILENT == 0,
        )
        assertTrue(
            "the alert is not the foreground notification",
            alert.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
        )
        assertEquals(
            "the recovered session's tea",
            "${TimerTestActivity.TEA} · Aufguss 1",
            alert.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        activeNotification(TimerNotifications.ID_LIVE_UPDATE)?.let {
            fail("notification 1 must be gone while alerting, ${flagsOf(it)} ($kill)\n${deviceState()}")
        }
        // Evidence of the AlertPlayer (the emulator runs without audio): its vibration, if the image has a vibrator.
        Log.i(
            TAG,
            "vibrations:\n" +
                shell("dumpsys vibrator_manager").lines().filter { it.contains(packageName) }.joinToString("\n"),
        )
    }

    @Test
    fun aStepKilledAndReopenedShowsItsStateAgainAndAlertsOnTime() {
        val started = TimeSource.Monotonic.markNow()
        context.startActivity(TimerTestActivity.intent(context, RELAUNCH_STEP.inWholeSeconds.toInt()))
        val outcome = eventually("the step's start in the timer process", START_TIMEOUT) {
            TimerTestActivity.outcomeFile(context).takeIf { it.exists() }?.readText()
        }
        assertEquals("test setup: start and Start of the step", TimerTestActivity.STARTED, outcome)
        val deadlineWallMs = TimerTestActivity.deadlineFile(context).readText().toLong()
        eventually("foreground BrewTimerService", START_TIMEOUT) {
            serviceBlock()?.takeIf { it.contains("isForeground=true") }
        }
        val kill = killTimer("the running step", TimerNotifications.ID_LIVE_UPDATE)

        // The user opens the app again: its process start recovers the step (04 §7.2).
        context.startActivity(TimerStateActivity.intent(context))
        val shown = eventually("the reopened timer's state", START_TIMEOUT) {
            TimerStateActivity.stateFile(context).takeIf { it.exists() }?.readText()
        }
        assertTrue(
            "test setup: too slow, ${started.elapsedNow()} of the $RELAUNCH_STEP step passed before the reopened state",
            started.elapsedNow() < RELAUNCH_STEP - KILL_MARGIN,
        )
        assertEquals("the reopened timer", "RUNNING|$deadlineWallMs|${TimerTestActivity.TEA}", shown)
        // Posted by a process after the kill: the reopened one, or the system's restart of the sticky service
        // (seen on API 36 about 4 s after the kill), which is as good a return; never one the kill left.
        eventually("notification 1 posted again after the kill ($kill)", START_TIMEOUT) {
            postedNotification(TimerNotifications.ID_LIVE_UPDATE)?.takeIf { it.postTime > kill.postedAfterWallMs }
        }
        // The app start's recovery started the service again. (The test runner's instrumentation
        // allows any start of this uid, so a refused start cannot show here; 04 §12.)
        eventually("foreground BrewTimerService after the reopening", START_TIMEOUT) {
            serviceBlock()?.takeIf { it.contains("isForeground=true") }
        }

        shell("input keyevent KEYCODE_SLEEP")
        eventually("the alert notification (ID 2) at the deadline", RELAUNCH_STEP + ALERT_TIMEOUT) {
            activeNotification(TimerNotifications.ID_ALERT)
        }
        eventually("the timer screen shown at the deadline", ALERT_TIMEOUT) { resumedAlertActivity() }
        val alert = notificationManager.activeNotifications.firstOrNull { it.id == TimerNotifications.ID_ALERT }
        assertNotNull("alert notification (ID 2) missing", alert)
        alert!!
        assertNotNull("the alert has no full-screen intent", alert.notification.fullScreenIntent)
        assertTrue(
            "the alert is marked FLAG_SILENT (force-grouped), flags=0x${Integer.toHexString(alert.notification.flags)}",
            alert.notification.flags and FLAG_SILENT == 0,
        )
        val lateMs = alert.postTime - deadlineWallMs
        Log.i(TAG, "alert posted $lateMs ms after the deadline")
        assertTrue("the alert came $lateMs ms after the deadline", lateMs in -EARLY_TOLERANCE_MS..LATE_TOLERANCE_MS)
    }

    /**
     * Two timers (D-098, #137; 04 §7.2 with two slots, §9.3): a brew step and a quick timer due together (the
     * quick timer started right after the step), through two kills.
     *
     * - Killed while both run, the app reopened: its process start restores both as they ran.
     * - At their deadlines the brew's alert comes on time and takes the screen; the quick timer's deadline is
     *   handled on time too, but its alert is held behind the brew's: no alert of its own, only notification 5
     *   without a full-screen intent.
     * - Killed again during the brew's alert, with the screen off: the brew's alert watchdog (or the system's
     *   restart of the service) starts a new process, whose recovery brings the brew's alert back and keeps the
     *   quick timer's held.
     * - *Abgegossen* releases it: it rings at once, as a new alert notification with its full-screen intent. The
     *   log shows the brew's alert sound first and the quick timer's once, last.
     */
    @Test
    fun aBrewStepAndAQuickTimerDueTogetherSurviveTwoKillsAndAlertTheBrewFirst() {
        // The timer process is dead after the setup: the log starts empty.
        logDirectory.deleteRecursively()
        val started = TimeSource.Monotonic.markNow()
        val (brewDeadline, quickDeadline) = startBothDueTogether()

        // 1. Killed while both run; the user opens the app again.
        val firstKill = killTimer("both timers running", TimerNotifications.ID_LIVE_UPDATE)
        reopenWithBoth(started, firstKill, brewDeadline, quickDeadline)

        // 2. The deadlines: the brew's alert on time, the quick timer's held behind it.
        shell("input keyevent KEYCODE_SLEEP")
        val brewAlert = awaitBrewAlertWithQuickHeld(brewDeadline)

        // 3. Killed again during the brew's alert, with the screen off: a new process brings it back.
        val replayed = killDuringBrewAlert(brewAlert)

        // 4. *Abgegossen* releases the quick timer's alert at once.
        pourAndAwaitQuickAlert(replayed)
    }

    /** Starts a brew step and a quick timer of [RELAUNCH_STEP] due together; their deadlines on the wall clock. */
    private fun startBothDueTogether(): Pair<Long, Long> {
        val seconds = RELAUNCH_STEP.inWholeSeconds.toInt()
        context.startActivity(TimerTestActivity.intent(context, seconds, quickSeconds = seconds))
        val outcome = eventually("both timers' start in the timer process", START_TIMEOUT) {
            TimerTestActivity.outcomeFile(context).takeIf { it.exists() }?.readText()
        }
        assertEquals("test setup: the step and the quick timer started", TimerTestActivity.STARTED, outcome)
        val brewDeadline = TimerTestActivity.deadlineFile(context).readText().toLong()
        val quickDeadline = TimerTestActivity.quickDeadlineFile(context).readText().toLong()
        assertTrue(
            "test setup: the quick timer must be due together with the brew, ${quickDeadline - brewDeadline} ms after",
            quickDeadline - brewDeadline in -EARLY_TOLERANCE_MS..TOGETHER_MS,
        )
        eventually("foreground BrewTimerService", START_TIMEOUT) {
            serviceBlock()?.takeIf { it.contains("isForeground=true") }
        }
        return brewDeadline to quickDeadline
    }

    /** The user opens the app after [kill]: both timers are restored as they ran, the brew the Live Update. */
    private fun reopenWithBoth(
        started: TimeSource.Monotonic.ValueTimeMark,
        kill: TimerKill,
        brewDeadline: Long,
        quickDeadline: Long,
    ) {
        context.startActivity(TimerStateActivity.intent(context))
        eventually("the reopened timers' state", START_TIMEOUT) {
            TimerStateActivity.stateFile(context).takeIf { it.exists() }?.readText()
        }
        assertTrue(
            "test setup: too slow, ${started.elapsedNow()} of the $RELAUNCH_STEP step passed before the reopened state",
            started.elapsedNow() < RELAUNCH_STEP - KILL_MARGIN,
        )
        assertEquals(
            "both timers restored as they ran",
            "BREW RUNNING|$brewDeadline\nQUICK RUNNING|$quickDeadline",
            TimerStateActivity.slotsFile(context).readText(),
        )
        eventually("notification 1 posted again after the kill ($kill)", START_TIMEOUT) {
            postedNotification(TimerNotifications.ID_LIVE_UPDATE)?.takeIf { it.postTime > kill.postedAfterWallMs }
        }
        eventually("the quick timer as notification 5 beside the brew's Live Update", START_TIMEOUT) {
            activeNotification(TimerNotifications.ID_OTHER)?.takeIf { it.title()?.startsWith(QUICK_TITLE) == true }
        }
    }

    /**
     * The brew's alert at [brewDeadline], on time, taking the screen; the quick timer's deadline handled on time as
     * the hold behind it (04 §9.3). Returns the brew's alert.
     */
    private fun awaitBrewAlertWithQuickHeld(brewDeadline: Long): StatusBarNotification {
        val brewAlert = eventually("the brew's alert (ID 2) at its deadline", RELAUNCH_STEP + ALERT_TIMEOUT) {
            postedNotification(TimerNotifications.ID_ALERT)
        }
        assertEquals("the alert at the deadlines", BREW_ALERT_TEXT, brewAlert.notification.text())
        val lateMs = brewAlert.postTime - brewDeadline
        Log.i(TAG, "the brew's alert posted $lateMs ms after its deadline")
        assertTrue(
            "the brew's alert came $lateMs ms after its deadline",
            lateMs in -EARLY_TOLERANCE_MS..LATE_TOLERANCE_MS,
        )
        eventually("the timer screen shown at the deadline", ALERT_TIMEOUT) { resumedAlertActivity() }
        val hold = eventually("the quick timer's deadline held behind the brew's alert, in the log", START_TIMEOUT) {
            quickDeadlineHeld()
        }
        Log.i(TAG, "the quick timer's deadline handled ${hold.deltaMs} ms late (${hold.source}), held")
        assertTrue(
            "the quick timer's deadline was handled ${hold.deltaMs} ms late",
            hold.deltaMs in -EARLY_TOLERANCE_MS..LATE_TOLERANCE_MS,
        )
        assertQuickAlertHeld("at the deadlines")
        return brewAlert
    }

    /**
     * Kills the timer process with the screen off while [brewAlert] still plays its cycle and the quick timer's alert
     * is held; a new process (the alert watchdog's, or the system's restart of the service) brings the brew's alert
     * back with the quick timer's still held. Returns the brew's alert again.
     */
    private fun killDuringBrewAlert(brewAlert: StatusBarNotification): StatusBarNotification {
        // The alert's full-screen stand-in turns the screen on when it opens (setTurnScreenOn), which can come
        // after the first key: the key is sent again until the screen stays off.
        eventually("the screen off during the brew's alert", START_TIMEOUT) {
            if (power.isInteractive) shell("input keyevent KEYCODE_SLEEP")
            Unit.takeIf { !power.isInteractive }
        }
        assertTrue(
            "test setup: too slow, the brew's alert must still play its cycle (SET-14) at the second kill",
            System.currentTimeMillis() - brewAlert.postTime < KILL_WITHIN_ALERT.inWholeMilliseconds,
        )
        val kill = killTimer("the brew alerting, the quick timer's alert held", TimerNotifications.ID_ALERT)
        val replayed = eventually("the brew's alert again from a new process", WATCHDOG + ALERT_TIMEOUT) {
            postedNotification(TimerNotifications.ID_ALERT)?.takeIf { it.postTime > kill.postedAfterWallMs }
        }
        assertNotEquals("the alert must come from a new timer process", kill.pid, timerPid())
        assertEquals("the alert after the second kill", BREW_ALERT_TEXT, replayed.notification.text())
        assertNotNull("the brew's alert has no full-screen intent", replayed.notification.fullScreenIntent)
        assertNotSilent(replayed.notification)
        assertQuickAlertHeld("after the second kill")
        return replayed
    }

    /**
     * *Abgegossen* in [brewAlert]: the quick timer's alert rings at once, a new alert notification with its
     * full-screen intent; the log's alert sounds are the brew's first and the quick timer's once, last (04 §9.3).
     */
    private fun pourAndAwaitQuickAlert(brewAlert: StatusBarNotification) {
        val pouredAtWallMs = System.currentTimeMillis()
        tap(brewAlert.notification, POURED)
        val quickAlert = eventually("the quick timer's alert (ID 2) after Abgegossen", START_TIMEOUT) {
            postedNotification(TimerNotifications.ID_ALERT)?.takeIf { it.notification.text() == QUICK_TITLE }
        }
        val releasedMs = quickAlert.postTime - pouredAtWallMs
        Log.i(TAG, "the quick timer's alert posted $releasedMs ms after Abgegossen")
        assertTrue(
            "the quick timer's alert came $releasedMs ms after Abgegossen",
            releasedMs in -EARLY_TOLERANCE_MS..LATE_TOLERANCE_MS,
        )
        assertNotNull("the released alert has no full-screen intent", quickAlert.notification.fullScreenIntent)
        assertNotSilent(quickAlert.notification)
        val sounds = eventually("the quick timer's alert sound in the log", START_TIMEOUT) {
            alertSounds().takeIf { it.lastOrNull() == TimerSlot.QUICK }
        }
        Log.i(TAG, "alert sounds in order: $sounds")
        assertTrue(
            "the brew's alert must sound first and the quick timer's once, last: $sounds",
            sounds.first() == TimerSlot.BREW && sounds.count { it == TimerSlot.QUICK } == 1,
        )
    }

    /**
     * The quick timer's alert is held: notification 5, without a full-screen intent; the alert (ID 2) is the
     * brew's, the only notification with one.
     */
    private fun assertQuickAlertHeld(what: String) {
        val other = eventually("the quick timer held as notification 5 $what", START_TIMEOUT) {
            activeNotification(TimerNotifications.ID_OTHER)?.takeIf { it.title()?.startsWith(QUICK_TITLE) == true }
        }
        Log.i(TAG, "notification 5 $what: ${other.title()} · ${other.text()}")
        assertNull("the held quick timer must not take the screen ($what)", other.fullScreenIntent)
        assertEquals(
            "only the brew's alert may take the screen ($what)",
            listOf(TimerNotifications.ID_ALERT),
            notificationManager.activeNotifications.filter { it.notification.fullScreenIntent != null }.map { it.id },
        )
        assertEquals("the alert $what", BREW_ALERT_TEXT, activeNotification(TimerNotifications.ID_ALERT)?.text())
    }

    private fun assertNotSilent(notification: Notification) = assertTrue(
        "the alert is marked FLAG_SILENT (force-grouped), flags=0x${Integer.toHexString(notification.flags)}",
        notification.flags and FLAG_SILENT == 0,
    )

    /** The quick timer's deadline in the log, handled as the hold behind the brew's alert (04 §9.3); null before. */
    private fun quickDeadlineHeld(): FireTime? = runBlocking { log.entries() }
        .filter { it.message.startsWith("event AlertHold ") }
        .firstNotNullOfOrNull { FireTime.parse(it.message) }
        ?.takeIf { it.slot == TimerSlot.QUICK && it.kind == TimerAlarmKind.DEADLINE }

    /** Whose alert sound started, in the order of the log (D-068). */
    private fun alertSounds(): List<TimerSlot> = runBlocking { log.entries() }.asReversed()
        .mapNotNull { FireTime.parsePlayed(it.message) }
        .filter { it.kind == TimerAlarmKind.DEADLINE }
        .map { it.slot }

    /** A kill of the timer process [pid]: a notification shown after it must be posted after [postedAfterWallMs]. */
    private data class TimerKill(val pid: Int, val postedAfterWallMs: Long, val left: String) {
        override fun toString() = "killed pid $pid; after the kill $left"
    }

    /**
     * Kills the timer process in [what] and waits until it is gone, then until Android removed the notification
     * [id] the killed service showed in the foreground, or a new timer process started. When the kill met an
     * update of it on its way, it stays, flagged, until a new process takes it over (D-084; seen on API 36 in CI
     * on 2026-10-30, with no new process for more than 10 s): it is logged, not failed. The result says after
     * when a notification [id] shown again must be posted: after the kill, and after the one the kill left.
     */
    private fun killTimer(what: String, id: Int): TimerKill {
        val pid = checkNotNull(timerPid()) { "no timer process in $what" }
        val killedAtWallMs = System.currentTimeMillis()
        Process.killProcess(pid)
        eventually("timer process $pid gone ($what)", START_TIMEOUT) { Unit.takeIf { timerPid() != pid } }
        val removed = eventuallyOrNull(START_TIMEOUT) {
            Unit.takeIf { activeNotification(id) == null || timerPid() != null }
        } != null
        val left = postedNotification(id)?.takeIf { !removed }
        val state = when {
            left != null -> "notification $id left by the kill, ${flagsOf(left.notification)} (D-084)"
            timerPid() != null -> "a new timer process ${timerPid()}"
            else -> "notification $id gone"
        }
        return TimerKill(pid, maxOf(killedAtWallMs, left?.postTime ?: 0L), state).also { Log.i(TAG, "$what: $it") }
    }

    /**
     * Kills the timer process during the step that started at [stepStartWallMs] and waits until
     * it is gone. A stress run (`scripts/stress-instrumented.sh`) can choose the moment of the
     * kill. Android removes the killed service's notification 1, unless the kill met an update
     * of it on its way: then it stays, flagged, for the next process to remove (D-084). Both
     * are logged and returned for a failure message.
     */
    private fun killDuringStep(started: TimeSource.Monotonic.ValueTimeMark, stepStartWallMs: Long): Kill {
        killAfterStartMs()?.let { Thread.sleep((stepStartWallMs + it - System.currentTimeMillis()).coerceAtLeast(0)) }
        val pid = checkNotNull(timerPid()) { "no timer process" }
        assertTrue(
            "test setup: too slow, ${started.elapsedNow()} of the $STEP step passed before the kill",
            started.elapsedNow() < STEP - KILL_MARGIN,
        )
        Process.killProcess(pid)
        val afterStartMs = System.currentTimeMillis() - stepStartWallMs
        eventually("timer process $pid gone", START_TIMEOUT) { Unit.takeIf { timerPid() != pid } }
        val liveUpdate = eventuallyOrNull(STALE_TIMEOUT) {
            Unit.takeIf { activeNotification(TimerNotifications.ID_LIVE_UPDATE) == null }
        }?.let { "gone" } ?: "still posted, ${flagsOf(activeNotification(TimerNotifications.ID_LIVE_UPDATE))}"
        return Kill(pid, afterStartMs, liveUpdate).also { Log.i(TAG, it.toString()) }
    }

    /** A kill of the timer process: when, and what it left of notification 1. */
    private data class Kill(val pid: Int, val afterStartMs: Long, val liveUpdateAfter: String) {
        override fun toString() =
            "killed pid $pid $afterStartMs ms after the step's start; notification 1 after the kill: $liveUpdateAfter"
    }

    /**
     * No timer from an earlier run: its alarms cancelled (same uid), its screens closed, its
     * process killed, its database deleted, its notifications removed. A leftover session would
     * refuse the start.
     *
     * Alarms and screens go first: a resumed [TimerScreenStandIn] (or an alarm) would start the
     * killed process again at once, so it is killed until it stays dead. A process that started
     * meanwhile recovered the old session at its start (04 §7.2), so it is killed once more
     * after the database is gone.
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
        TimerTestActivity.deadlineFile(context).delete()
        TimerTestActivity.quickDeadlineFile(context).delete()
        TimerStateActivity.stateFile(context).delete()
        TimerStateActivity.slotsFile(context).delete()
        notificationManager.cancelAll()
    }

    private fun killTimerProcess() = eventually("timer process gone", START_TIMEOUT) {
        timerPid()?.let(Process::killProcess)
        Unit.takeIf { timerPid() == null }
    }

    /** The PID of the test APK's main process, where the timer runs; null while it is dead. */
    private fun timerPid(): Int? = activityManager.runningAppProcesses
        ?.firstOrNull { it.processName == packageName }
        ?.pid

    private fun serviceBlock(): String? {
        val lines = shell("dumpsys activity services $packageName").lines()
        val start = lines.indexOfFirst { it.contains("ServiceRecord{") && it.contains(SERVICE) }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { lines[it].contains("* ServiceRecord{") } ?: lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    private fun deadlineAlarm(): String? {
        val action = checkNotNull(TimerAlarmReceiver.intent(context, TimerSlot.BREW, TimerAlarmKind.DEADLINE).action)
        return shell("dumpsys alarm").lines().firstOrNull { it.contains("tag=") && it.contains(action) }
    }

    /** The alert's screen as the resumed activity now (not `mLastResumedActivity` of a closed one). */
    private fun resumedAlertActivity(): String? = shell("dumpsys activity activities").lines()
        .firstOrNull { it.contains("ResumedActivity") && !it.contains("LastResumed") && it.contains(ALERT_ACTIVITY) }

    private fun activeNotification(id: Int): Notification? = postedNotification(id)?.notification

    /** Notification [id] as posted, with its post time (that of its last post or update). */
    private fun postedNotification(id: Int): StatusBarNotification? =
        notificationManager.activeNotifications.firstOrNull { it.id == id }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    /** Taps the action [label] of [notification], as the user does in the shade or on the lock screen. */
    private fun tap(notification: Notification, label: String) {
        val action = notification.actions?.firstOrNull { it.title.toString() == label }
        assertNotNull("no action $label in ${notification.actions?.map { it.title }}", action)
        action!!.actionIntent.send()
    }

    private fun flagsOf(notification: Notification?): String =
        notification?.let { "flags=0x${Integer.toHexString(it.flags)}" } ?: "gone meanwhile"

    /**
     * The instrumentation argument `umamitKillAfterStartMs` (stress runs only): the kill waits
     * until this many ms after the step's start, once the setup checks passed.
     */
    private fun killAfterStartMs(): Long? = InstrumentationRegistry.getArguments()
        .getString(ARG_KILL_AFTER_START_MS)
        ?.toLongOrNull()
        ?.also { require(it in 0..MAX_KILL_AFTER_START_MS) { "$ARG_KILL_AFTER_START_MS out of range: $it" } }

    /** Polls [poll] until it returns non-null or [timeout] passes; null then, without failing. */
    private fun <T : Any> eventuallyOrNull(timeout: Duration, poll: () -> T?): T? {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            poll()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return poll()
    }

    private fun wakeAndUnlock() = UnlockedDevice.unlock()

    /** Polls [poll] until it returns non-null or [timeout] passes; then fails with [what] and the device state. */
    private fun <T : Any> eventually(what: String, timeout: Duration, poll: () -> T?): T {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            poll()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        poll()?.let { return it }
        throw AssertionError("Timed out after $timeout waiting for: $what\n${deviceState()}")
    }

    private fun deviceState(): String = buildString {
        appendLine("SDK=${Build.VERSION.SDK_INT} timerPid=${timerPid()}")
        appendLine("canUseFullScreenIntent=${notificationManager.canUseFullScreenIntent()}")
        appendLine("--- service ---")
        appendLine(serviceBlock() ?: "(none)")
        appendLine("--- alarms ---")
        appendLine(shell("dumpsys alarm").lines().filter { it.contains(packageName) }.joinToString("\n"))
        appendLine("--- notifications ---")
        notificationManager.activeNotifications.forEach {
            appendLine(
                "id=${it.id} channel=${it.notification.channelId} flags=0x${Integer.toHexString(
                    it.notification.flags,
                )} posted=${it.postTime} title=${it.notification.title()} text=${it.notification.text()}",
            )
        }
        appendLine("--- activities ---")
        appendLine(
            shell("dumpsys activity activities").lines()
                .filter { line -> ACTIVITY_MARKERS.any(line::contains) }
                .joinToString("\n") { it.trim() },
        )
    }.also { Log.e(TAG, it) }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private companion object {
        const val TAG = "ProcessDeathTest"

        /** Long enough for the start, the kill and the screen to go off on a slow CI emulator. */
        val STEP = 25.seconds
        val START_TIMEOUT = 10.seconds

        /** The kill must leave this much of the step, so the alert cannot fire in the old process. */
        val KILL_MARGIN = 5.seconds

        /** How long the log waits for Android to remove the killed service's notification 1. */
        val STALE_TIMEOUT = 2.seconds

        const val ARG_KILL_AFTER_START_MS = "umamitKillAfterStartMs"
        const val MAX_KILL_AFTER_START_MS = 15_000L

        /** Margin for the alarm, the process start, the recovery, the service and the activity launch. */
        val ALERT_TIMEOUT = 45.seconds

        /** Long enough for the start, the kill and the reopening on a slow CI emulator. */
        val RELAUNCH_STEP = 35.seconds

        /**
         * The alert on time (TMR-30): the PO's phone must stay within 1 s (the accuracy run, #52);
         * the CI emulator gets 2 s for its slower service start.
         */
        const val LATE_TOLERANCE_MS = 2_000L

        /** The wall clock may differ from the elapsed clock by a few ms; the alert is never early. */
        const val EARLY_TOLERANCE_MS = 50L
        const val POLL_INTERVAL_MS = 250L

        /** `Notification.FLAG_SILENT` (`@hide`, API 36), set on force-grouped notifications (D-044). */
        const val FLAG_SILENT = 0x00020000

        /** Two timers: the quick timer's deadline at most this long after the brew's counts as due together. */
        const val TOGETHER_MS = 1_000L

        /** How often the alert watchdog comes while an alert plays (`AlertWatchdog.INTERVAL_MS`, D-063). */
        val WATCHDOG = 30.seconds

        /**
         * The second kill comes this soon after the brew's alert, well within its cycle of up to 15 s (SET-14
         * *Normal*), so the alert has not run it and recovery plays it again (D-047).
         */
        val KILL_WITHIN_ALERT = 9.seconds

        /** The quick timer's title and its alert's text; the brew's alert's text. */
        const val QUICK_TITLE = "Schnelltimer"
        const val BREW_ALERT_TEXT = "${TimerTestActivity.TEA} · Aufguss 1"
        const val POURED = "Abgegossen"

        val SERVICE: String = BrewTimerService::class.java.simpleName
        val ALERT_ACTIVITY: String = TimerScreenStandIn::class.java.simpleName
        val ACTIVITY_MARKERS = listOf("ResumedActivity", "TimerScreenStandIn", "mKeyguardShowing", "mOccluded")
    }
}
