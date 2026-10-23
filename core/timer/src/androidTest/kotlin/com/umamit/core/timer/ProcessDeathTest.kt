package com.umamit.core.timer

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umamit.core.common.SystemWallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.alarm.TimerAlarmReceiver
import com.umamit.core.timer.controller.AndroidMonotonicClock
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.service.BrewTimerService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
 *   notification, not silenced (D-044), and names the recovered session's tea.
 * - Or the user opens the app again before the deadline: its process start recovers the step,
 *   which shows as it was, and the alert still comes on time.
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
        eventually("foreground BrewTimerService", START_TIMEOUT) {
            serviceBlock()?.takeIf { it.contains("isForeground=true") }
        }
        eventually("the deadline alarm in dumpsys alarm", START_TIMEOUT) { deadlineAlarm() }

        val killed = checkNotNull(timerPid()) { "no timer process" }
        assertTrue(
            "test setup: too slow, ${started.elapsedNow()} of the $STEP step passed before the kill",
            started.elapsedNow() < STEP - KILL_MARGIN,
        )
        Process.killProcess(killed)
        eventually("timer process $killed gone", START_TIMEOUT) { Unit.takeIf { timerPid() != killed } }
        assertNotNull("the deadline alarm must survive the process", deadlineAlarm())

        // With the screen off the full-screen intent launches the activity (on and
        // unlocked it would be a heads-up, 04 §8.2).
        shell("input keyevent KEYCODE_SLEEP")
        eventually("the timer screen shown at the deadline", STEP + ALERT_TIMEOUT) { resumedAlertActivity() }

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
        assertNull("notification 1 must be gone while alerting", activeNotification(TimerNotifications.ID_LIVE_UPDATE))
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
        val killed = checkNotNull(timerPid()) { "no timer process" }
        Process.killProcess(killed)
        eventually("timer process $killed gone", START_TIMEOUT) { Unit.takeIf { timerPid() != killed } }
        // The service's notification went with its process, so its return below is the reopening's.
        eventually("notification 1 gone with the killed process", START_TIMEOUT) {
            Unit.takeIf { activeNotification(TimerNotifications.ID_LIVE_UPDATE) == null }
        }

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
        eventually("notification 1 back after the reopening", START_TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)
        }
        // The app start's recovery started the service again. (The test runner's instrumentation
        // allows any start of this uid, so a refused start cannot show here; 04 §12.)
        eventually("foreground BrewTimerService after the reopening", START_TIMEOUT) {
            serviceBlock()?.takeIf { it.contains("isForeground=true") }
        }

        shell("input keyevent KEYCODE_SLEEP")
        eventually("the timer screen shown at the deadline", RELAUNCH_STEP + ALERT_TIMEOUT) {
            resumedAlertActivity()
        }
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
        alarms.reconcile(DueTimes.NONE)
        alarms.watchAlert(null)
        activityManager.appTasks.forEach { it.finishAndRemoveTask() }
        killTimerProcess()
        alarms.reconcile(DueTimes.NONE)
        alarms.watchAlert(null)
        context.deleteDatabase(UmamiDatabase.NAME)
        killTimerProcess()
        alarms.reconcile(DueTimes.NONE)
        alarms.watchAlert(null)
        TimerTestActivity.outcomeFile(context).delete()
        TimerTestActivity.deadlineFile(context).delete()
        TimerStateActivity.stateFile(context).delete()
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
        val action = checkNotNull(TimerAlarmReceiver.intent(context, TimerAlarmKind.DEADLINE).action)
        return shell("dumpsys alarm").lines().firstOrNull { it.contains("tag=") && it.contains(action) }
    }

    private fun resumedAlertActivity(): String? = shell("dumpsys activity activities").lines()
        .firstOrNull { it.contains("ResumedActivity") && it.contains(ALERT_ACTIVITY) }

    private fun activeNotification(id: Int): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun wakeAndUnlock() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
    }

    /** Polls [probe] until it returns non-null or [timeout] passes; then fails with [what] and the device state. */
    private fun <T : Any> eventually(what: String, timeout: Duration, probe: () -> T?): T {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            probe()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        probe()?.let { return it }
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
                )}",
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

        val SERVICE: String = BrewTimerService::class.java.simpleName
        val ALERT_ACTIVITY: String = TimerScreenStandIn::class.java.simpleName
        val ACTIVITY_MARKERS = listOf("ResumedActivity", "TimerScreenStandIn", "mKeyguardShowing", "mOccluded")
    }
}
