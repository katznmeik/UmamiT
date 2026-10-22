package com.umamit.core.timer.diagnostics

import android.Manifest
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
}
