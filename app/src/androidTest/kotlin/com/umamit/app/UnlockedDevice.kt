package com.umamit.app

import android.app.KeyguardManager
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Wakes the device and removes the keyguard. The lock-screen tests enable the swipe keyguard
 * (the CI AVD has it disabled), and on API 35 `wm dismiss-keyguard` alone sometimes leaves it
 * showing: every later activity then starts behind it. So the dismissal is retried, with
 * KEYCODE_MENU (which dismisses an insecure keyguard), until the keyguard is gone.
 */
object UnlockedDevice {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** Fails with the keyguard state when it does not go away within [timeout]. */
    fun unlock(timeout: Duration = UNLOCK_TIMEOUT) {
        val keyguard = instrumentation.targetContext.getSystemService(KeyguardManager::class.java)
        val start = TimeSource.Monotonic.markNow()
        var attempt = 0
        while (start.elapsedNow() < timeout) {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            if (attempt > 0) shell("input keyevent KEYCODE_MENU")
            Thread.sleep(POLL_MS)
            if (!keyguard.isKeyguardLocked) return
            attempt++
        }
        throw AssertionError(
            "test setup: the keyguard is still showing after $timeout " +
                "(keyguardLocked=${keyguard.isKeyguardLocked})\n" +
                shell("dumpsys window").lines().filter { "mKeyguardShowing" in it || "mDreamingLockscreen" in it }
                    .joinToString("\n"),
        )
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private val UNLOCK_TIMEOUT = 10.seconds
    private const val POLL_MS = 500L
}

/** Starts each test on an awake, unlocked device; put it before the rule that launches an activity. */
class UnlockedDeviceRule : ExternalResource() {
    override fun before() = UnlockedDevice.unlock()
}
