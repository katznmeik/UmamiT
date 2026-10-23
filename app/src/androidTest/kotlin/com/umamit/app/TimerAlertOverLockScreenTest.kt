package com.umamit.app

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
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
import com.umamit.core.timer.controller.BrewSessionController
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.TimerHost
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
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
        // *Brühen starten* and *Start* in the app, then the app is closed.
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

        compose.onNodeWithText(context.getString(BrewR.string.brew_timer_poured)).performClick()
        eventually("FINISHED after Abgegossen", TIMEOUT) {
            (controller.state.value as? TimerUiState.Active)?.takeIf { it.phase == TimerPhase.FINISHED }
        }
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
            steps = listOf(PlannedStep(StepKind.INFUSION, STEP.inWholeSeconds.toInt(), tempC = 60)),
            recipePreAlertSeconds = 0,
            preAlert = PreAlertChoice.Off,
        )
    }
}
