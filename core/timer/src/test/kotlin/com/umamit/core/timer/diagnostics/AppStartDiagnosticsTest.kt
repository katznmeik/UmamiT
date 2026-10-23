package com.umamit.core.timer.diagnostics

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnostic
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The permission states at each process start (06 §5.6): notifications, Live
 * Updates, the full-screen alert, exact alarms and the battery exemption,
 * logged once, so a missed alert can be matched to a missing permission.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppStartDiagnosticsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val diagnostics = FakeTimerDiagnostics()

    @Test
    fun `the permission states are logged with the time of the start`() {
        val states = PermissionStates(
            notifications = true,
            promotedNotifications = false,
            fullScreenIntent = true,
            exactAlarms = true,
            batteryUnrestricted = false,
        )

        AppStartDiagnostics({ states }, TimerClocks(clock, clock), diagnostics).record()

        assertThat(diagnostics.entries).containsExactly(TimerDiagnostic.PermissionsAtStart(clock.reading(), states))
    }

    @Test
    fun `a check that throws is not fatal and logs nothing`() {
        AppStartDiagnostics({ error("binder died (test)") }, TimerClocks(clock, clock), diagnostics).record()

        assertThat(diagnostics.entries).isEmpty()
    }

    @Test
    fun `the device reader reports the notification permission`() {
        val reader = AndroidPermissionStatesReader(app)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(reader.read().notifications).isFalse()

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(reader.read().notifications).isTrue()
    }

    @Test
    fun `the device reader reports a denied full-screen app op as missing (D-068)`() {
        val reader = AndroidPermissionStatesReader(app)

        setFullScreenMode(AppOpsManager.MODE_ERRORED)
        assertThat(reader.fullScreenOpMode()).isEqualTo(AppOpsManager.MODE_ERRORED)
        setFullScreenMode(AppOpsManager.MODE_ALLOWED)
        assertThat(reader.fullScreenOpMode()).isEqualTo(AppOpsManager.MODE_ALLOWED)

        // The permission check alone still said yes after `appops … deny` on API 35 and 36.
        assertThat(fullScreenAlertAllowed(permission = true, AppOpsManager.MODE_ERRORED)).isFalse()
        assertThat(fullScreenAlertAllowed(permission = true, AppOpsManager.MODE_IGNORED)).isFalse()
        assertThat(fullScreenAlertAllowed(permission = true, AppOpsManager.MODE_ALLOWED)).isTrue()
        assertThat(fullScreenAlertAllowed(permission = true, AppOpsManager.MODE_DEFAULT)).isTrue()
        assertThat(fullScreenAlertAllowed(permission = false, AppOpsManager.MODE_ALLOWED)).isFalse()
    }

    /** `appops set <package> USE_FULL_SCREEN_INTENT <mode>`, through the hidden `setMode(String, …)`. */
    private fun setFullScreenMode(mode: Int) {
        val appOps = app.getSystemService(AppOpsManager::class.java)
        AppOpsManager::class.java
            .getMethod("setMode", String::class.java, Int::class.java, String::class.java, Int::class.java)
            .invoke(appOps, "android:use_full_screen_intent", app.applicationInfo.uid, app.packageName, mode)
    }
}
