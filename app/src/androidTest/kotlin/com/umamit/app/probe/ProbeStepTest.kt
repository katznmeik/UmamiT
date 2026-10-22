package com.umamit.app.probe

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.umamit.app.MainActivity
import com.umamit.app.R
import com.umamit.core.timer.TimerAlarm
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.designsystem.R as DesignSystemR
import com.umamit.core.timer.probe.ProbeAlertActivity
import com.umamit.core.timer.probe.ProbeTimer
import com.umamit.core.timer.probe.ProbeTimerService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import com.umamit.core.timer.R as TimerR

/**
 * Probe step in the CI emulator job (M1-03a, #15; plan §3.1 seam 2), driven
 * from the outside: one tap on the probe screen, then the platform state is
 * checked with `dumpsys` and the notification manager:
 * - exact alarms for the pre-alert and the deadline are pending;
 * - the `specialUse` foreground service runs with the Live Update (ID 1);
 * - API 36: the Live Update requests promotion and, with the user's Live
 *   Update toggle on, is promoted (FLAG_PROMOTED_ONGOING); API 35: a normal
 *   ongoing notification;
 * - at the deadline (shortened step, screen off) the alert replaces the Live
 *   Update, is not marked silent, and its full-screen activity is shown;
 *   *Abgegossen* stops the service.
 *
 * Background for both API 36 checks: `docs/plans/M1-probe-api-notes.md`.
 */
@RunWith(AndroidJUnit4::class)
class ProbeStepTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** The promotion toggle as it was before [allowPromotion] changed it; restored in [tearDown]. */
    private var promotionToRestore: Boolean? = null

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set $packageName USE_FULL_SCREEN_INTENT allow")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        ProbeTimer.stop(context)
    }

    @After
    fun tearDown() {
        ProbeTimer.stop(context)
        ProbeTimer.stepDurationOverride = null
        promotionToRestore?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) setPromotionAllowed(it) }
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .plus(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED))
                .filterIsInstance<ProbeAlertActivity>()
                .forEach(Activity::finish)
        }
        // Leave the emulator awake and unlocked for the next test.
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
    }

    @Test
    fun oneTapStartsExactAlarmsForegroundServiceAndLiveUpdate() {
        // Long enough that nothing fires while the state is checked.
        ProbeTimer.stepDurationOverride = 90.seconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) allowPromotion()

        tapStartOnProbeScreen()

        // Exact alarms (04 §6.2).
        val alarmDump = eventually("pre-alert and deadline alarms in dumpsys alarm", ALARM_TIMEOUT) {
            shell("dumpsys alarm").takeIf {
                Dumpsys.alarmBlock(it, TimerAlarm.PRE_ALERT.action) != null &&
                    Dumpsys.alarmBlock(it, TimerAlarm.DEADLINE.action) != null
            }
        }
        listOf(TimerAlarm.PRE_ALERT, TimerAlarm.DEADLINE).forEach { alarm ->
            val block = checkNotNull(Dumpsys.alarmBlock(alarmDump, alarm.action))
            Log.i(TAG, "dumpsys alarm, $alarm:\n$block")
            assertTrue("$alarm is not an exact alarm:\n$block", Dumpsys.isExact(block))
            assertTrue("$alarm is not ELAPSED_WAKEUP:\n$block", block.contains("ELAPSED_WAKEUP"))
        }

        // Foreground service (04 §6.3).
        val serviceBlock = awaitForegroundService()
        Log.i(TAG, "dumpsys activity services:\n$serviceBlock")

        // Live Update (04 §8.3).
        val liveUpdate = eventually("Live Update notification (ID 1)", ALARM_TIMEOUT) {
            activeNotification(TimerNotifications.ID_LIVE_UPDATE)
        }
        assertEquals(TimerNotifications.CHANNEL_BREW_TIMER, liveUpdate.channelId)
        assertTrue("Live Update is not ongoing", liveUpdate.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(
            "Live Update is not the FGS notification",
            liveUpdate.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            assertTrue(
                "API 36: the Live Update must request promotion (android.requestPromotedOngoing)",
                NotificationCompat.isRequestPromotedOngoing(liveUpdate),
            )
            assertPromoted()
        } else {
            assertFalse(
                "API ${Build.VERSION.SDK_INT}: the Live Update must be a normal ongoing notification",
                liveUpdate.flags and Dumpsys.FLAG_PROMOTED_ONGOING != 0,
            )
        }
    }

    @Test
    fun deadlineShowsTheFullScreenAlertAndAbgegossenStopsTheService() {
        ProbeTimer.stepDurationOverride = SHORT_STEP

        tapStartOnProbeScreen()
        awaitForegroundService()

        // With the screen off the full-screen intent launches the activity
        // (with the screen on and unlocked it would be a heads-up, 04 §8.2).
        shell("input keyevent KEYCODE_SLEEP")

        val alert = eventually("ProbeAlertActivity shown at the deadline", SHORT_STEP + ALERT_TIMEOUT) {
            alertActivity()
        }
        Log.i(TAG, "System UI decisions:\n${systemUiInterruptionDecisions()}")
        val alertNotification = activeNotification(TimerNotifications.ID_ALERT)
        assertNotNull("alert notification (ID 2) missing", alertNotification)
        alertNotification!!
        assertEquals(TimerNotifications.CHANNEL_STEEP_FINISHED, alertNotification.channelId)
        assertEquals(Notification.CATEGORY_ALARM, alertNotification.category)
        assertNotNull("alert has no full-screen intent", alertNotification.fullScreenIntent)
        // Android 16 force-groups two ungrouped notifications of an app and marks
        // them FLAG_SILENT; System UI then suppresses the full-screen intent and
        // the heads-up. The alert therefore replaces the Live Update.
        assertTrue(
            "alert is marked FLAG_SILENT (force-grouped), flags=0x${Integer.toHexString(alertNotification.flags)}",
            alertNotification.flags and Dumpsys.FLAG_SILENT == 0,
        )
        assertTrue(
            "alert is not the FGS notification",
            alertNotification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
        )
        assertNull(
            "the Live Update (ID 1) must be gone while the alert is shown",
            activeNotification(TimerNotifications.ID_LIVE_UPDATE),
        )

        // *Abgegossen* stops the step and the foreground service.
        instrumentation.runOnMainSync { alert.findViewById<Button>(TimerR.id.probe_alert_done).performClick() }
        eventually("foreground service stopped after Abgegossen", ALARM_TIMEOUT) {
            Unit.takeIf { Dumpsys.serviceBlock(shell("dumpsys activity services $packageName"), SERVICE) == null }
        }
    }

    private fun tapStartOnProbeScreen() {
        // The probe entry lives in the settings, behind the gear on Start (M1-05).
        composeRule.onNodeWithContentDescription(context.getString(DesignSystemR.string.ds_settings)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.probe_entry)).performClick()
        // The probe screen scrolls (status list above the step section); performClick
        // taps the node centre even off-screen, so bring it into view first.
        composeRule.onNodeWithText(context.getString(R.string.probe_step_start)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun awaitForegroundService(): String = eventually("foreground ProbeTimerService", ALARM_TIMEOUT) {
        Dumpsys.serviceBlock(shell("dumpsys activity services $packageName"), SERVICE)
            ?.takeIf { it.contains("isForeground=true") }
    }

    /**
     * Turns the app's Live Update toggle on, as the PO does on the phone
     * (Settings › Apps › UmamiT › Benachrichtigungen). On the API 36 emulator
     * image it is off by default: Android 16 keeps it in
     * NotificationManagerService (`PreferencesHelper.canHavePromotedNotifs`,
     * default = the flag `android.app.ui_rich_ongoing`; with the flag on, the
     * appop `POST_PROMOTED_NOTIFICATIONS`). No `cmd notification` command sets
     * it, so this uses the test API that the Settings page uses
     * (`setCanBePromoted(…, fromUser = true)`).
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
     * `NotificationManager.setCanPostPromotedNotifications(pkg, uid, allowed)`
     * is a `@TestApi` (instrumented processes may call test APIs). The service
     * accepts it from the shell or with `STATUS_BAR_SERVICE`, which the shell
     * holds and lends to this test for the call.
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

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun assertPromoted() {
        val canPromote = notificationManager.canPostPromotedNotifications()
        val dump = eventually("promoted Live Update in dumpsys notification", ALARM_TIMEOUT) {
            shell("dumpsys notification --noredact").takeIf { dump ->
                Dumpsys.notificationRecords(dump, packageName, TimerNotifications.ID_LIVE_UPDATE)
                    .any(Dumpsys::showsPromotedOngoing)
            }
        }
        val records = Dumpsys.notificationRecords(dump, packageName, TimerNotifications.ID_LIVE_UPDATE)
        Log.i(TAG, "dumpsys notification, Live Update:\n${records.joinToString("\n---\n")}")
        val posted = checkNotNull(activeNotification(TimerNotifications.ID_LIVE_UPDATE))
        Log.i(TAG, "Promotion rule set:\n${promotionRuleSet(posted)}")
        assertTrue(
            "FLAG_PROMOTED_ONGOING missing on the posted notification (canPostPromotedNotifications=$canPromote, " +
                "hasPromotableCharacteristics=${posted.hasPromotableCharacteristics()})",
            posted.flags and Notification.FLAG_PROMOTED_ONGOING != 0,
        )
    }

    private fun activeNotification(id: Int): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun alertActivity(): ProbeAlertActivity? {
        var found: ProbeAlertActivity? = null
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            found = (monitor.getActivitiesInStage(Stage.RESUMED) + monitor.getActivitiesInStage(Stage.STARTED))
                .filterIsInstance<ProbeAlertActivity>()
                .firstOrNull()
        }
        return found
    }

    /** Every lifecycle stage a [ProbeAlertActivity] is in (launched but not visible shows as CREATED/STOPPED). */
    private fun alertActivityStages(): String {
        var stages = ""
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            stages = Stage.entries
                .filter { stage -> monitor.getActivitiesInStage(stage).any { it is ProbeAlertActivity } }
                .joinToString()
                .ifEmpty { "(no ProbeAlertActivity in any stage)" }
        }
        return stages
    }

    /**
     * Which Android 16 rule set this image uses (`docs/plans/M1-probe-api-notes.md`):
     * the posted variant and the relevant aconfig flags, as far as the shell can read them.
     */
    private fun promotionRuleSet(posted: Notification): String = buildString {
        appendLine("colorized=${posted.extras.getBoolean(Notification.EXTRA_COLORIZED)}")
        appendLine("requestPromotedOngoing=${NotificationCompat.isRequestPromotedOngoing(posted)}")
        appendLine("hasPromotableCharacteristics=${posted.hasPromotableCharacteristics()}")
        appendLine(Dumpsys.matchingLines(shell("aflags list"), FLAG_NAMES).ifEmpty { "(aflags: no output)" })
    }

    /** System UI's interruption log for this app (`FSI allowed/suppressed: <reason>`), last lines. */
    private fun systemUiInterruptionDecisions(): String {
        val dump = shell("dumpsys activity service com.android.systemui/.SystemUIService NotifInterruptLog")
        return Dumpsys.matchingLines(dump, listOf(packageName))
            .lines()
            .filter { line -> SYSTEM_UI_DECISIONS.any(line::contains) }
            .takeLast(SYSTEM_UI_LINES)
            .joinToString("\n")
            .ifEmpty { "(no System UI decision lines for $packageName)" }
    }

    /**
     * Polls [probe] until it returns non-null or [timeout] passes; on timeout
     * fails with [what] and the last raw `dumpsys` sections for diagnosis.
     */
    private fun <T : Any> eventually(what: String, timeout: Duration, probe: () -> T?): T {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            probe()?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        probe()?.let { return it }
        throw AssertionError("Timed out after $timeout waiting for: $what\n${diagnostics()}")
    }

    private fun diagnostics(): String {
        val notifications = shell("dumpsys notification --noredact")
        val records = Dumpsys.notificationRecords(notifications, packageName, TimerNotifications.ID_LIVE_UPDATE) +
            Dumpsys.notificationRecords(notifications, packageName, TimerNotifications.ID_ALERT)
        val alarms = shell("dumpsys alarm")
        return buildString {
            appendLine("SDK=${Build.VERSION.SDK_INT}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                appendLine("canPostPromotedNotifications=${notificationManager.canPostPromotedNotifications()}")
            }
            appendLine("canUseFullScreenIntent=${notificationManager.canUseFullScreenIntent()}")
            appendLine("status=${ProbeTimer.status.value}")
            appendLine("--- services ---")
            appendLine(Dumpsys.serviceBlock(shell("dumpsys activity services $packageName"), SERVICE) ?: "(none)")
            appendLine("--- alarms ---")
            TimerAlarm.entries.forEach { appendLine(Dumpsys.alarmBlock(alarms, it.action) ?: "($it: none)") }
            appendLine("--- notification records ---")
            appendLine(records.joinToString("\n---\n").ifEmpty { "(none)" })
            appendLine("--- alert activity ---")
            appendLine(alertActivityStages())
            appendLine(Dumpsys.matchingLines(shell("dumpsys activity activities"), ACTIVITY_MARKERS))
            appendLine("--- power ---")
            appendLine(Dumpsys.matchingLines(shell("dumpsys power"), POWER_MARKERS))
            appendLine("--- System UI interruption decisions ---")
            appendLine(systemUiInterruptionDecisions())
            appendLine("--- flags ---")
            appendLine(Dumpsys.matchingLines(shell("aflags list"), FLAG_NAMES))
        }.also { Log.e(TAG, it) }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private companion object {
        const val TAG = "ProbeStepTest"
        val SERVICE: String = ProbeTimerService::class.java.simpleName
        val SHORT_STEP = 15.seconds
        val ALARM_TIMEOUT = 10.seconds

        /** Generous margin for alarm delivery, FGS start and the activity launch on a slow CI emulator. */
        val ALERT_TIMEOUT = 45.seconds
        const val POLL_INTERVAL_MS = 250L

        /** Not in the SDK stubs as a `Manifest.permission` constant (system permission). */
        const val STATUS_BAR_SERVICE = "android.permission.STATUS_BAR_SERVICE"

        /** Android 16 flags behind the promotion rules and the force-grouping (api notes). */
        val FLAG_NAMES = listOf(
            "android.app.api_rich_ongoing",
            "android.app.ui_rich_ongoing",
            "android.app.opt_in_rich_ongoing",
            "android.service.notification.notification_force_grouping",
            "android.service.notification.notification_silent_flag",
        )
        val ACTIVITY_MARKERS = listOf("ResumedActivity", "ProbeAlertActivity", "mKeyguardShowing", "mOccluded")
        val POWER_MARKERS = listOf("mWakefulness=", "Display Power: state=", "mIsPowered=")
        val SYSTEM_UI_DECISIONS = listOf("FSI ", "PEEK", "PULSE")
        const val SYSTEM_UI_LINES = 20
    }
}
