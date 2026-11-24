package com.umamit.app

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import android.view.Display
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.UnlockedDevice
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.send
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.feature.brew.timer.TIMER_RING_TAG
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import com.umamit.feature.brew.R as BrewR

/**
 * The alert of the real app in the CI emulator job (API 35/36; #47, TMR-06, 04 §8.2; spec #40
 * seam 5): a step runs through the app's own controller, service and notifier with the app
 * closed; at the deadline the alert's full-screen intent opens the app's timer route (SCR-05)
 * over the lock screen, the alert is the foreground notification and not `FLAG_SILENT`
 * (D-044), and *Abgegossen* on the timer screen ends the step.
 *
 * With the phone held in landscape (D-136 Q5, build note „Portrait timer"; SCR-05, NFR-COMP-02) the alert brings the
 * timer up upright, in the app and over the lock screen, and the timer stays upright and the session intact while the
 * phone turns during the alert, with no new activity (the activity handles a rotation in place); the frames of those
 * turns are reported only. These cases live here, not in `TransitionJankTest`, because only this class rings a real
 * alert: that one never does (rule 8).
 */
@RunWith(AndroidJUnit4::class)
class TimerAlertOverLockScreenTest {
    @get:Rule(order = 0)
    val setupDone = SetupDoneRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    /** Whether the AVD had its lock screen disabled before the test enabled it; restored in [tearDown]. */
    private var lockScreenWasDisabled = false

    private val recorder = FrameRecorder()
    private val rotation = DisplayRotation(::shell)
    private val creations = MainActivityCreations()

    private val controller =
        (context.applicationContext as TimerHost).timerInputs as BrewSessionController

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        shell("appops set $packageName USE_FULL_SCREEN_INTENT allow")
        // The CI AVD boots with the lock screen disabled, so the keyguard never shows after sleep.
        // „Over the lock screen" needs one: enable the (insecure, swipe) keyguard for this test.
        lockScreenWasDisabled = shell("locksettings get-disabled").trim() == "true"
        shell("locksettings set-disabled false")
        wakeAndUnlock()
        endTimer()
    }

    @After
    fun tearDown() {
        creations.stop()
        rotation.restore()
        recorder.release()
        endTimer()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().let { monitor ->
                Stage.entries.flatMap { monitor.getActivitiesInStage(it) }
            }.filterIsInstance<MainActivity>().forEach { it.finish() }
        }
        if (lockScreenWasDisabled) shell("locksettings set-disabled true")
        leaveUnlocked()
    }

    @Test
    fun theAlertOpensTheTimerOverTheLockScreen() {
        // *Session starten* and *Start* in the app, then the app is closed.
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking {
                assertEquals(TransitionOutcome.Accepted, controller.startSession(brew()))
                assertEquals(TransitionOutcome.Accepted, controller.send(TimerEvent.Start))
            }
        }
        eventually("the Live Update", TIMEOUT) { activeNotification(TimerNotifications.ID_LIVE_UPDATE) }

        // With the screen off and locked the full-screen intent opens the timer (on and unlocked: a heads-up).
        shell("input keyevent KEYCODE_SLEEP")
        val locked = eventuallyOrNull(LOCK_TIMEOUT) { Unit.takeIf { keyguard.isKeyguardLocked } } != null
        if (!locked) Log.w(TAG, "No keyguard after sleep on this image; checking the screen turned on instead")
        eventually("the alert (ID 2) at the deadline", STEP + ALERT_TIMEOUT) {
            activeNotification(TimerNotifications.ID_ALERT)
        }
        eventually("MainActivity resumed by the full-screen intent", ALERT_TIMEOUT) { resumedMainActivity() }

        check("the alert must turn the screen on", power.isInteractive)
        if (locked) check("the timer must show over the lock screen", keyguard.isKeyguardLocked)
        // The caption sits in the ring's description (TimerRing clears its semantics); the pill is a text.
        compose.onNodeWithTag(TIMER_RING_TAG)
            .assert(hasContentDescription(context.getString(BrewR.string.brew_timer_caption_alert), substring = true))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(BrewR.string.brew_timer_poured)).assertIsDisplayed()
        val alert = activeNotification(TimerNotifications.ID_ALERT)
        check("alert notification (ID 2) missing", alert != null)
        alert!!
        check("the alert has no full-screen intent", alert.fullScreenIntent != null)
        check(
            "the alert is marked FLAG_SILENT (force-grouped), flags=0x${Integer.toHexString(alert.flags)}",
            alert.flags and Dumpsys.FLAG_SILENT == 0,
        )
        check("the alert is not the foreground notification", alert.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        check(
            "the alert must be the app's only timer notification",
            notificationManager.activeNotifications.none {
                it.id == TimerNotifications.ID_LIVE_UPDATE || it.id == TimerNotifications.ID_QUIET
            },
        )

        // Frame cost of the snap, reported only (NFR-PERF-12, audit A-20 „Measurable"); no assertion on it. The click
        // happens whatever the measurement does: should it fail before the click, the click follows outside it, so
        // the rule-8 checks below never depend on measuring.
        var clicked = false
        val clickPoured = {
            compose.onNodeWithText(context.getString(BrewR.string.brew_timer_poured)).performClick()
            clicked = true
        }
        val frames = runCatching { recorder.measure(SETTLE_MS) { clickPoured() } }
            .onFailure { Log.w(TAG, "Abgegossen frames not measured: ${it.message}") }
            .getOrNull()
        if (!clicked) clickPoured()
        if (frames != null) reportFrames(frames)
        eventually("FINISHED after Abgegossen", TIMEOUT) {
            (controller.state.value as? TimerUiState.Active)?.takeIf { it.phase == TimerPhase.FINISHED }
        }
    }

    /**
     * In the app on Journal held in landscape (D-136 Q5): unlocked and in front, the system shows the alert as a
     * heads-up, and its tap sends the alert's content intent, sent here; the timer comes up upright with the alert, and
     * turning the phone during the alert creates no new activity.
     */
    @Test
    fun theAlertBringsTheTimerUpUprightInTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val session = startOnLandscapeJournal()
            val created = creations.count
            val alert = eventually("the alert (ID 2) at the deadline", STEP + ALERT_TIMEOUT) {
                activeNotification(TimerNotifications.ID_ALERT)
            }
            val tap = alert.contentIntent
            check("the alert has no content intent", tap != null)
            tap!!.send()
            checkAlertUpright(session, created, "the alert brought up in the app")
            turnDuringAlert("alert turned in the app", session, created)
            pour()
        }
    }

    /** Over the lock screen, the app stopped behind it on Journal held in landscape (D-136 Q5): no new activity. */
    @Test
    fun theAlertOverTheLockScreenComesUpUprightFromALandscapeJournal() = overLockScreenFromLandscape(appClosed = false)

    /**
     * Over the lock screen, the app closed while held in landscape (D-136 Q5): the full-screen intent creates the
     * activity once, upright, and never again.
     */
    @Test
    fun theAlertOverTheLockScreenComesUpUprightWithTheAppClosed() = overLockScreenFromLandscape(appClosed = true)

    private fun overLockScreenFromLandscape(appClosed: Boolean) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val session = startOnLandscapeJournal()
            if (appClosed) scenario.close()
            val created = creations.count + if (appClosed) 1 else 0
            eventually("the Live Update", TIMEOUT) { activeNotification(TimerNotifications.ID_LIVE_UPDATE) }
            shell("input keyevent KEYCODE_SLEEP")
            val locked = eventuallyOrNull(LOCK_TIMEOUT) { Unit.takeIf { keyguard.isKeyguardLocked } } != null
            check("no keyguard after sleep on this image", locked)
            eventually("the alert (ID 2) at the deadline", STEP + ALERT_TIMEOUT) {
                activeNotification(TimerNotifications.ID_ALERT)
            }
            eventually("MainActivity resumed by the full-screen intent", ALERT_TIMEOUT) { resumedMainActivity() }
            check("the alert must turn the screen on", power.isInteractive)
            check("the timer must show over the lock screen", keyguard.isKeyguardLocked)
            checkAlertUpright(session, created, "the alert over the lock screen")
            turnDuringAlert("alert turned over lock screen", session, created)
            check("the timer must still show over the lock screen", keyguard.isKeyguardLocked)
            pour()
        } finally {
            if (!appClosed) scenario.close()
        }
    }

    /**
     * The app on Journal, turned to landscape and checked turned, then a step started there; the session's id. Starts
     * the rotation and the count of new activities.
     */
    private fun startOnLandscapeJournal(): String {
        rotation.save()
        creations.start()
        compose.onNodeWithTag(tabTag(Tab.Journal)).performClick()
        compose.waitForIdle()
        rotation.landscape()
        eventually("Journal turned to landscape", TIMEOUT) { Upright.read()?.takeIf { it.landscape } }
        runBlocking {
            assertEquals(TransitionOutcome.Accepted, controller.startSession(brew()))
            assertEquals(TransitionOutcome.Accepted, controller.send(TimerEvent.Start))
        }
        val session = (controller.state.value as? TimerUiState.Active)?.sessionId
        check("the started step has no session", session != null)
        return session!!
    }

    /** The alerting timer upright, with no activity created beyond [created], and the same [session] alerting. */
    private fun checkAlertUpright(session: String, created: Int, what: String) {
        eventually("$what upright", TIMEOUT) { Upright.read()?.takeIf { it.portrait } }
        compose.onNodeWithTag(TIMER_RING_TAG)
            .assert(hasContentDescription(context.getString(BrewR.string.brew_timer_caption_alert), substring = true))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(BrewR.string.brew_timer_poured)).assertIsDisplayed()
        check("${creations.count - created} new MainActivity for $what", creations.count == created)
        val active = controller.state.value as? TimerUiState.Active
        check("$what: the session changed or stopped alerting", active?.sessionId == session && active.alerting)
    }

    /**
     * The phone turned upright and back to landscape during the alert: after each turn the timer is still upright, no
     * activity was created and the session still alerts. The frames are reported only, and a failed measurement never
     * skips a turn or a check.
     */
    private fun turnDuringAlert(transition: String, session: String, created: Int) {
        val budgetNs = budgetNs()
        val runs = listOf(rotation::portrait, rotation::landscape).mapNotNull { turn ->
            val frames = runCatching { recorder.measure(SETTLE_MS) { turn() } }
                .onFailure { Log.w(TAG, "$transition frames not measured: ${it.message}") }
                .getOrNull()
            if (frames == null) {
                turn()
                Thread.sleep(SETTLE_MS)
            }
            val upright = Upright.read()
            check("$transition: the timer is not upright: $upright", upright?.portrait == true)
            checkAlertUpright(session, created, transition)
            frames?.let { JankRun(it, budgetNs) }
        }
        if (runs.isNotEmpty()) Log.i(TAG, JankReport(transition, runs).line(budgetNs, recorder.windowCount, null))
    }

    /** *Abgegossen* ends the step. */
    private fun pour() {
        compose.onNodeWithText(context.getString(BrewR.string.brew_timer_poured)).performClick()
        eventually("FINISHED after Abgegossen", TIMEOUT) {
            (controller.state.value as? TimerUiState.Active)?.takeIf { it.phase == TimerPhase.FINISHED }
        }
    }

    /** One `TransitionJank:` line for the *Abgegossen* click over the lock screen; report-only, never an assertion. */
    private fun reportFrames(frames: List<Long>) {
        val budgetNs = budgetNs()
        val report = JankReport("Abgegossen over lock screen", listOf(JankRun(frames, budgetNs)))
        Log.i(TAG, report.line(budgetNs, recorder.windowCount, null))
    }

    /** The frame budget from the display's refresh rate (D-117: 60 Hz, 16,7 ms). */
    private fun budgetNs(): Long {
        val refreshRate = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 0f
        return if (refreshRate > 0f) (NANOS_PER_SECOND / refreshRate).roundToLong() else DEFAULT_BUDGET_NS
    }

    /** Ends a session left by an earlier test, so the start is not refused (TMR-21). */
    private fun endTimer() = runBlocking {
        if (controller.state.value is TimerUiState.Active) controller.send(TimerEvent.Discard)
    }

    private fun resumedMainActivity(): MainActivity? {
        var found: MainActivity? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>()
                .firstOrNull()
        }
        return found
    }

    private fun activeNotification(id: Int): Notification? =
        notificationManager.activeNotifications.firstOrNull { it.id == id }?.notification

    private fun wakeAndUnlock() = UnlockedDevice.unlock()

    /** Leaves the device awake and unlocked for the next test class (the keyguard may linger). */
    private fun leaveUnlocked() = UnlockedDevice.unlock()

    /** Fails with [message] and the device's state unless [condition]. */
    private fun check(message: String, condition: Boolean) {
        if (!condition) throw AssertionError("$message\n${deviceState()}")
    }

    private fun deviceState(): String {
        val notifications = notificationManager.activeNotifications.joinToString("\n") {
            "id=${it.id} channel=${it.notification.channelId} flags=0x${Integer.toHexString(it.notification.flags)}"
        }
        val activities = Dumpsys.matchingLines(
            shell("dumpsys activity activities"),
            listOf("ResumedActivity", "mKeyguardShowing", "mOccluded"),
        )
        return "state=${controller.state.value}\nkeyguardLocked=${keyguard.isKeyguardLocked} " +
            "interactive=${power.isInteractive}\n--- notifications ---\n$notifications\n--- activities ---\n$activities"
    }

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

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private companion object {
        val STEP = 15.seconds
        val TIMEOUT = 10.seconds
        val ALERT_TIMEOUT = 45.seconds
        val LOCK_TIMEOUT = 5.seconds
        const val TAG = "TimerAlertTest"
        const val SETTLE_MS = 1_200L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val DEFAULT_BUDGET_NS = 16_666_667L
        const val POLL_INTERVAL_MS = 250L

        fun brew() = BrewStart(
            snapshot = SessionSnapshot(
                teaId = null,
                teaName = "Testtee",
                teaSecondaryName = null,
                teaType = null,
                styleKey = null,
                category = null,
                profileId = null,
                profileName = null,
                method = BrewMethod.KYUSU,
                vesselId = null,
                vesselName = null,
                leafGrams = null,
                waterMl = null,
                baseTempC = 60,
            ),
            steps = listOf(
                PlannedStep(StepKind.INFUSION, STEP.inWholeSeconds.toInt(), tempC = 60, preAlertSeconds = 0),
            ),
            recipePreAlertSeconds = 0,
        )
    }
}
