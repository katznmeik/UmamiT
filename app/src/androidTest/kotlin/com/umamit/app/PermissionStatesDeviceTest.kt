package com.umamit.app

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umamit.core.timer.diagnostics.AndroidPermissionStatesReader
import com.umamit.core.timer.diagnostics.PermissionStates
import com.umamit.core.timer.reliability.CheckState
import com.umamit.core.timer.reliability.ReliabilityCheck
import com.umamit.core.timer.reliability.toReliabilityStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reliability checks (TMR-31, 06 §5.6) against a real system, in the CI emulator job (API 35
 * and 36): [AndroidPermissionStatesReader] follows the states a test can switch from the shell
 * without killing the app. Robolectric covers the mapping and the settings pages
 * (`TimerReliabilityTest`); this covers the platform queries, which the M1-03a status list
 * verified on device before #54 removed it. It runs in the app's test APK, against the app's own
 * package and merged manifest, like that status list did; in core:timer's library test APK the
 * full-screen app op did not change what the reader saw (CI on 58a3e17 and 96edeef).
 *
 * - Full-screen intent: `appops set … USE_FULL_SCREEN_INTENT deny|allow`.
 * - Battery optimisation: `cmd deviceidle whitelist -|+…`.
 * - Notifications: granted with `pm grant` (revoking a runtime permission kills the app process,
 *   so the denied case stays with the unit tests).
 * - Exact alarms: always granted through `USE_EXACT_ALARM`.
 * - Live Updates: unavailable before Android 16, a real answer on Android 16.
 */
@RunWith(AndroidJUnit4::class)
class PermissionStatesDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName
    private val reader = AndroidPermissionStatesReader(context)

    @Before
    fun setUp() {
        shell("pm grant $packageName android.permission.POST_NOTIFICATIONS")
    }

    @After
    fun restoreDefaults() {
        shell("appops set $packageName USE_FULL_SCREEN_INTENT default")
        shell("cmd deviceidle whitelist -$packageName")
    }

    @Test
    fun grantedNotificationsAndExactAlarmsReadAsOk() {
        val states = readUntil("notifications and exact alarms granted") { it.notifications && it.exactAlarms }
        val status = states.toReliabilityStatus()
        assertEquals(CheckState.Ok, status.stateOf(ReliabilityCheck.Notifications))
        assertEquals(CheckState.Ok, status.stateOf(ReliabilityCheck.ExactAlarms))
    }

    @Test
    fun liveUpdatesAreUnavailableOnlyBeforeAndroid16() {
        val promoted = reader.read().promotedNotifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            assertNotNull("API 36 answers canPostPromotedNotifications()", promoted)
        } else {
            assertNull("API 35 has no promotion API", promoted)
            assertEquals(
                CheckState.Unavailable,
                reader.read().toReliabilityStatus().stateOf(ReliabilityCheck.LiveUpdates),
            )
        }
    }

    @Test
    fun fullScreenAlertFollowsTheAppOp() {
        shell("appops set $packageName USE_FULL_SCREEN_INTENT deny")
        readUntil("full-screen intent denied") { !it.fullScreenIntent }

        shell("appops set $packageName USE_FULL_SCREEN_INTENT allow")
        readUntil("full-screen intent allowed") { it.fullScreenIntent }
    }

    @Test
    fun batteryFollowsTheExemption() {
        shell("cmd deviceidle whitelist -$packageName")
        val restricted = readUntil("battery optimised") { !it.batteryUnrestricted }
        assertEquals(CheckState.Missing, restricted.toReliabilityStatus().stateOf(ReliabilityCheck.Battery))

        shell("cmd deviceidle whitelist +$packageName")
        readUntil("battery unrestricted") { it.batteryUnrestricted }
    }

    /** Reads until [expected] holds; system state (app ops, permission caches) can take a moment. */
    private fun readUntil(what: String, expected: (PermissionStates) -> Boolean): PermissionStates {
        val deadline = SystemClock.uptimeMillis() + READ_TIMEOUT_MS
        while (true) {
            val states = reader.read()
            if (expected(states)) return states
            assertTrue("$what: still $states", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(READ_RETRY_MS)
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private companion object {
        const val READ_TIMEOUT_MS = 10_000L
        const val READ_RETRY_MS = 250L
    }
}
