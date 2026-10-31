package com.umamit.core.timer

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umamit.core.common.SystemWallClock
import com.umamit.core.database.UmamiDatabase
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.alarm.AndroidTimerAlarms
import com.umamit.core.timer.controller.AndroidMonotonicClock
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnosticsPort
import com.umamit.core.timer.engine.DueTimes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The real timer's notifications in the CI emulator job (API 35/36; #47, 04 §8, §12; spec #40
 * seam 5), through the real controller, service port, notifier and service in the main
 * process of this test APK:
 *
 * - a running step is the Live Update (ID 1), the foreground notification; on API 36 it
 *   requests promotion and, with the user's Live Update toggle on, is promoted
 *   (`FLAG_PROMOTED_ONGOING`); on API 35 it is a normal ongoing notification (the fallback);
 * - *Pause* and *Weiter* in the notification reach the controller;
 * - at the deadline the alert (ID 2) replaces the Live Update, is not `FLAG_SILENT` (D-044),
 *   and its full-screen intent opens the timer screen over the lock screen; *Abgegossen* in
 *   the notification ends the step;
 * - a long step's quiet notification (ID 4, D-064) gives way to the alert alone;
 * - with a quick timer alongside, the Live Update is the timer due next and the other one is
 *   notification 5, never promoted (D-098).
 *
 * Background for the API 36 checks: `docs/plans/M1-android16-api-notes.md`.
 */
@RunWith(AndroidJUnit4::class)
class TimerNotificationsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    private val power = context.getSystemService(PowerManager::class.java)

    /** The promotion toggle as it was before the test changed it; restored in [tearDown]. */
    private var promotionToRestore: Boolean? = null

    /** Whether the AVD had its lock screen disabled before the test enabled it; restored in [tearDown]. */
    private var lockScreenWasDisabled = false

    @Before
    fun setUp() {
        assertNotEquals(
            "test setup: the runner must not run in the timer's process (targetProcesses in the androidTest manifest)",
            packageName,
            Application.getProcessName(),
        )
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set $packageName USE_FULL_SCREEN_INTENT allow")
        // The CI AVD boots with the lock screen disabled, so the keyguard never shows after sleep.
        // „Over the lock screen" needs one: enable the (insecure, swipe) keyguard for this test.
        lockScreenWasDisabled = shell("locksettings get-disabled").trim() == "true"
        shell("locksettings set-disabled false")
        resetTimer()
        wakeAndUnlock()
    }

    @After
    fun tearDown() {
        resetTimer()
        promotionToRestore?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) setPromotionAllowed(it) }
        if (lockScreenWasDisabled) shell("locksettings set-disabled true")
        leaveUnlocked()
    }

    @Test
    fun aRunningStepIsThePromotedLiveUpdateAndItsActionsReachTheController() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) allowPromotion()
        startStep(RUNNING_STEP)

        // The service starts with READY's Live Update (*Brühen starten*, D-053); *Start* updates it.
        val liveUpdate = eventually("the running step's Live Update (ID 1) as the foreground notification", TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)
                ?.takeIf {
                    it.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 && it.text() == "Zieht bei 60\u00A0°C"
                }
        }
        assertEquals(TimerNotifications.CHANNEL_BREW_TIMER, liveUpdate.channelId)
        check("the Live Update is not ongoing", liveUpdate.flags and Notification.FLAG_ONGOING_EVENT != 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            assertTrue(
                "API 36: the Live Update must request promotion",
                NotificationCompat.isRequestPromotedOngoing(liveUpdate),
            )
            val promoted = eventually("FLAG_PROMOTED_ONGOING on the Live Update", TIMEOUT) {
                activeNotification(TimerNotifications.ID_LIVE_UPDATE)
                    ?.takeIf { it.flags and Notification.FLAG_PROMOTED_ONGOING != 0 }
            }
            Log.i(TAG, "promoted Live Update, flags=0x${Integer.toHexString(promoted.flags)}")
        } else {
            assertFalse(
                "API ${Build.VERSION.SDK_INT}: the Live Update must be a normal ongoing notification",
                liveUpdate.flags and FLAG_PROMOTED_ONGOING != 0,
            )
        }

        tap(liveUpdate, "Pause")
        val paused = eventually("Pausiert after Pause in the notification", TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)?.takeIf { it.text()?.startsWith("Pausiert") == true }
        }
        tap(paused, "Weiter")
        eventually("Zieht again after Weiter in the notification", TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)?.takeIf { it.text() == "Zieht bei 60\u00A0°C" }
        }
    }

    /**
     * Two timers (04 §8.3, D-098, PO Q5): the Live Update (ID 1, promoted on API 36) is the timer that ends
     * next, the other one is notification 5 on `timer_other`, never promoted. Pausing the quick timer moves
     * the Live Update to the brew; *Abbrechen* on notification 5 leaves the brew's Live Update alone.
     */
    @Test
    fun theLiveUpdateFollowsTheTimerDueNextAndTheOtherTimerIsNeverPromoted() {
        val promotion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
        if (promotion) allowPromotion()
        startStep(RUNNING_STEP, quick = QUICK_STEP)

        val quickLiveUpdate = eventually("the quick timer's Live Update (ID 1), ending first", TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)?.takeIf {
                it.title() == QUICK_TITLE && it.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 &&
                    (!promotion || it.flags and FLAG_PROMOTED_ONGOING != 0)
            }
        }
        assertTrue("the Live Update must request promotion", !promotion || isRequestPromoted(quickLiveUpdate))
        assertOtherNotPromoted(titleStart = TimerTestActivity.TEA)

        tap(quickLiveUpdate, "Pause")
        eventually("the brew's Live Update (ID 1) after the quick timer's Pause", TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)?.takeIf {
                it.title()?.startsWith(TimerTestActivity.TEA) == true &&
                    (!promotion || it.flags and FLAG_PROMOTED_ONGOING != 0)
            }
        }
        val pausedQuick = assertOtherNotPromoted(titleStart = QUICK_TITLE, textStart = "Pausiert")

        tap(pausedQuick, "Abbrechen")
        eventually("notification 5 gone after Abbrechen", TIMEOUT) {
            Unit.takeIf { activeNotification(TimerNotifications.ID_OTHER) == null }
        }
        check(
            "the brew's Live Update must be the timer's only notification",
            notificationManager.activeNotifications.map { it.id } == listOf(TimerNotifications.ID_LIVE_UPDATE),
        )
        val brew = checkNotNull(activeNotification(TimerNotifications.ID_LIVE_UPDATE))
        check(
            "notification 1 must show the brew, was ${brew.title()}",
            brew.title()?.startsWith(TimerTestActivity.TEA) == true,
        )
    }

    /** Notification 5 shows the timer of [titleStart] (and [textStart]), on `timer_other`, never promoted. */
    private fun assertOtherNotPromoted(titleStart: String, textStart: String = ""): Notification {
        val other = eventually("notification 5 of $titleStart $textStart", TIMEOUT) {
            activeNotification(TimerNotifications.ID_OTHER)?.takeIf {
                it.title()?.startsWith(titleStart) == true && it.text().orEmpty().startsWith(textStart)
            }
        }
        assertEquals(TimerNotifications.CHANNEL_TIMER_OTHER, other.channelId)
        assertFalse("notification 5 must not request promotion", isRequestPromoted(other))
        check(
            "notification 5 must never be promoted, flags=0x${Integer.toHexString(other.flags)}",
            other.flags and FLAG_PROMOTED_ONGOING == 0,
        )
        return other
    }

    private fun isRequestPromoted(notification: Notification) =
        NotificationCompat.isRequestPromotedOngoing(notification)

    @Test
    fun theAlertTakesOverTheLockScreenAloneAndAbgegossenEndsTheStep() {
        startStep(SHORT_STEP)
        eventually("the Live Update", TIMEOUT) { activeNotification(TimerNotifications.ID_LIVE_UPDATE) }

        // With the screen off the full-screen intent opens the timer screen (on and unlocked it
        // would be a heads-up, 04 §8.2).
        val locked = sleepAndLock()
        val alert = awaitAlertOverLockScreen(SHORT_STEP, locked)

        check("alert channel ${alert.channelId}", alert.channelId == TimerNotifications.CHANNEL_STEEP_FINISHED)
        check("alert category ${alert.category}", alert.category == Notification.CATEGORY_ALARM)
        check(
            "the alert is not the foreground notification",
            alert.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
        )
        check(
            "the alert must be the app's only notification (no Live Update, no quiet one)",
            notificationManager.activeNotifications.map { it.id } == listOf(TimerNotifications.ID_ALERT),
        )

        tap(alert, "Abgegossen")
        val finished = eventually("the session finished after Abgegossen in the notification", TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)?.takeIf { it.text() == "Tippen zum Beenden" }
        }
        assertNull("the alert must be gone after Abgegossen", activeNotification(TimerNotifications.ID_ALERT))
        assertEquals("${TimerTestActivity.TEA} · Alle Aufgüsse fertig", finished.title())
    }

    @Test
    fun aLongStepsQuietNotificationGivesWayToTheAlertAlone() {
        // A cold brew: every step is long and runs without the service, on the quiet
        // notification (ID 4, D-064). At the deadline the alert must still be the app's only
        // notification, not force-grouped into the quiet one's group and not FLAG_SILENT (D-044).
        startStep(COLD_BREW_STEP, coldBrew = true)
        val quiet = eventually("the quiet notification (ID 4) of the long step", TIMEOUT) {
            activeNotification(TimerNotifications.ID_QUIET)
        }
        assertEquals(TimerNotifications.CHANNEL_BREW_TIMER, quiet.channelId)
        assertTrue("the long step must say when it is done", quiet.text()?.startsWith("Fertig um") == true)

        val locked = sleepAndLock()
        awaitAlertOverLockScreen(COLD_BREW_STEP, locked)

        check(
            "the alert must be the app's only notification (no quiet one, no autogroup summary)",
            notificationManager.activeNotifications.map { it.id } == listOf(TimerNotifications.ID_ALERT),
        )
    }

    /**
     * Turns the screen off and waits for the keyguard; true when it shows. The setup enables the
     * lock screen; should an image still not show it, the alert is checked over a screen it had
     * to turn on instead (see [awaitAlertOverLockScreen]).
     */
    private fun sleepAndLock(): Boolean {
        shell("input keyevent KEYCODE_SLEEP")
        val locked = eventuallyOrNull(LOCK_TIMEOUT) { Unit.takeIf { keyguard.isKeyguardLocked } } != null
        if (!locked) Log.w(TAG, "No keyguard after sleep on this image; checking the screen turned on instead")
        return locked
    }

    /**
     * Waits for the alert (ID 2) of a step of [step], then for the timer screen its full-screen
     * intent opens, resumed now (never a stale record of an earlier test), and checks the alert:
     * full-screen intent, not `FLAG_SILENT` (D-044), the screen turned on, and the timer over the
     * keyguard when [locked].
     */
    private fun awaitAlertOverLockScreen(step: Duration, locked: Boolean): Notification {
        val alert = eventually("the alert (ID 2) at the deadline", step + ALERT_TIMEOUT) {
            activeNotification(TimerNotifications.ID_ALERT)
        }
        eventually("the timer screen resumed by the full-screen intent", ALERT_TIMEOUT) { resumedTimerScreen() }
        check("the alert has no full-screen intent", alert.fullScreenIntent != null)
        check(
            "the alert is marked FLAG_SILENT (force-grouped), flags=0x${Integer.toHexString(alert.flags)}",
            alert.flags and FLAG_SILENT == 0,
        )
        check("the alert must turn the screen on", power.isInteractive)
        if (locked) check("the timer must show over the lock screen", keyguard.isKeyguardLocked)
        return alert
    }

    /** Fails with [message] and the device's state (notifications, activities, keyguard) unless [condition]. */
    private fun check(message: String, condition: Boolean) {
        if (!condition) throw AssertionError("$message\n${deviceState()}")
    }

    private fun startStep(step: Duration, coldBrew: Boolean = false, quick: Duration = Duration.ZERO) {
        context.startActivity(
            TimerTestActivity.intent(
                context,
                step.inWholeSeconds.toInt(),
                coldBrew,
                quickSeconds = quick.inWholeSeconds.toInt(),
            ),
        )
        val outcome = eventually("the step's start in the timer process", TIMEOUT) {
            TimerTestActivity.outcomeFile(context).takeIf { it.exists() }?.readText()
        }
        assertEquals("test setup: start and Start of the step", TimerTestActivity.STARTED, outcome)
    }

    /** Taps the action [label] of [notification], as the user does in the shade or on the lock screen. */
    private fun tap(notification: Notification, label: String) {
        val action = notification.actions?.firstOrNull { it.title.toString() == label }
        assertNotNull("no action $label in ${notification.actions?.map { it.title }}", action)
        action!!.actionIntent.send()
    }

    /**
     * Turns the app's Live Update toggle on, as the PO does on the phone
     * (Settings › Apps › UmamiT › Benachrichtigungen). On the API 36 emulator image it is off
     * by default: Android 16 keeps it in NotificationManagerService
     * (`PreferencesHelper.canHavePromotedNotifs`, default = the flag `android.app.ui_rich_ongoing`;
     * with the flag on, the appop `POST_PROMOTED_NOTIFICATIONS`). No `cmd notification` command
     * sets it, so this uses the test API that the Settings page uses.
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun allowPromotion() {
        if (notificationManager.canPostPromotedNotifications()) return
        promotionToRestore = false
        setPromotionAllowed(true)
        assertTrue(
            "test setup: canPostPromotedNotifications() still false after allowing promotion",
            notificationManager.canPostPromotedNotifications(),
        )
    }

    /**
     * `NotificationManager.setCanPostPromotedNotifications(pkg, uid, allowed)` is a `@TestApi`
     * (instrumented processes may call test APIs). The service accepts it from the shell or with
     * `STATUS_BAR_SERVICE`, which the shell holds and lends to this test for the call.
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun setPromotionAllowed(allowed: Boolean) {
        val uiAutomation = instrumentation.uiAutomation
        uiAutomation.adoptShellPermissionIdentity(STATUS_BAR_SERVICE)
        try {
            NotificationManager::class.java
                .getMethod(
                    "setCanPostPromotedNotifications",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                )
                .invoke(notificationManager, packageName, Process.myUid(), allowed)
        } catch (e: ReflectiveOperationException) {
            throw AssertionError("test setup: could not set the Live Update toggle to $allowed", e)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    /**
     * No timer from an earlier test: alarms cancelled (the alert watchdog too, D-063), screens
     * closed, process killed, database deleted. A process that started meanwhile recovered the
     * old session at its start (04 §7.2), so it is killed once more after the database is gone.
     */
    private fun resetTimer() {
        val clocks = TimerClocks(AndroidMonotonicClock(context), SystemWallClock)
        val alarms = AndroidTimerAlarms(context, clocks, TimerDiagnosticsPort {})
        val cancelAlarms = {
            TimerSlot.entries.forEach { alarms.reconcile(it, DueTimes.NONE) }
            TimerSlot.entries.forEach { alarms.watchAlert(it, null) }
        }
        cancelAlarms()
        activityManager.appTasks.forEach { it.finishAndRemoveTask() }
        killTimerProcess()
        cancelAlarms()
        context.deleteDatabase(UmamiDatabase.NAME)
        killTimerProcess()
        cancelAlarms()
        TimerTestActivity.outcomeFile(context).delete()
        TimerTestActivity.deadlineFile(context).delete()
        notificationManager.cancelAll()
    }

    private fun killTimerProcess() = eventually("timer process gone", TIMEOUT) {
        timerPid()?.let(Process::killProcess)
        Unit.takeIf { timerPid() == null }
    }

    private fun timerPid(): Int? = activityManager.runningAppProcesses
        ?.firstOrNull { it.processName == packageName }
        ?.pid

    /** The resumed timer screen; `mLastResumedActivity` may still name one an earlier test closed. */
    private fun resumedTimerScreen(): String? = shell("dumpsys activity activities").lines()
        .firstOrNull {
            it.contains("ResumedActivity") && !it.contains("LastResumed") &&
                it.contains(TimerScreenStandIn::class.java.simpleName)
        }

    private fun activeNotification(id: Int): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun wakeAndUnlock() = UnlockedDevice.unlock()

    /** Leaves the device awake and unlocked for the next test class (the keyguard may linger). */
    private fun leaveUnlocked() = UnlockedDevice.unlock()

    private fun <T : Any> eventuallyOrNull(timeout: Duration, poll: () -> T?): T? {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            poll()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return poll()
    }

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
        appendLine("keyguardLocked=${keyguard.isKeyguardLocked} interactive=${power.isInteractive}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            appendLine("canPostPromotedNotifications=${notificationManager.canPostPromotedNotifications()}")
        }
        appendLine("--- notifications ---")
        notificationManager.activeNotifications.forEach {
            appendLine(
                "id=${it.id} channel=${it.notification.channelId} " +
                    "flags=0x${Integer.toHexString(it.notification.flags)} text=${it.notification.text()}",
            )
        }
        appendLine("--- activities ---")
        appendLine(
            shell("dumpsys activity activities").lines()
                .filter { line -> ACTIVITY_MARKERS.any(line::contains) }
                .joinToString("\n") { it.trim() },
        )
        // The foreground service as ActivityManager sees it: its foreground ID and notification (D-071).
        appendLine("--- services ---")
        appendLine(
            shell("dumpsys activity services $packageName").lines()
                .filter { line -> SERVICE_MARKERS.any(line::contains) }
                .joinToString("\n") { it.trim() },
        )
    }.also { Log.e(TAG, it) }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private companion object {
        const val TAG = "TimerNotificationsTest"
        val RUNNING_STEP = 120.seconds
        val QUICK_STEP = 60.seconds
        const val QUICK_TITLE = "Schnelltimer"
        val COLD_BREW_STEP = 20.seconds
        val SHORT_STEP = 15.seconds
        val TIMEOUT = 10.seconds
        val ALERT_TIMEOUT = 45.seconds
        val LOCK_TIMEOUT = 5.seconds
        const val POLL_INTERVAL_MS = 250L

        /** `Notification.FLAG_PROMOTED_ONGOING` (API 36) as a plain bit, so it can be checked on API 35 too. */
        const val FLAG_PROMOTED_ONGOING = 0x00040000

        /** `Notification.FLAG_SILENT` (`@hide`, API 36), set on force-grouped notifications (D-044). */
        const val FLAG_SILENT = 0x00020000

        /** Not in the SDK stubs as a `Manifest.permission` constant (system permission). */
        const val STATUS_BAR_SERVICE = "android.permission.STATUS_BAR_SERVICE"

        val ACTIVITY_MARKERS = listOf("ResumedActivity", "TimerScreenStandIn", "mKeyguardShowing", "mOccluded")
        val SERVICE_MARKERS = listOf(
            "ServiceRecord",
            "isForeground",
            "foregroundNoti",
            "fgRequired",
            "startRequested",
            "lastStartId",
            "createTime",
        )
    }
}
