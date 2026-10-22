package com.umamit.core.timer.alarm

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.timer.TestTimerHost
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.service.BrewTimerService
import com.umamit.core.timer.service.BrewTimerServicePort
import com.umamit.core.timer.service.ForegroundServiceState
import com.umamit.core.timer.service.testState
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** The alarm receiver hands every alarm to the app's controller as its kind (04 §6.2, D-057). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestTimerHost::class)
class TimerAlarmReceiverTest {
    private val app: TestTimerHost = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
    }

    @After
    fun tearDown() = ForegroundServiceState.reset()

    @Test
    fun everyAlarmBroadcastReachesTheControllerAsItsKind() {
        TimerAlarmKind.entries.forEach { kind ->
            app.sendBroadcast(TimerAlarmReceiver.intent(app, kind))
            shadowOf(Looper.getMainLooper()).idle()

            assertThat(app.alarms.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(kind)
        }
        assertThat(app.alarms).isEmpty()
    }

    @Test
    fun anAlarmSendsAStartAndroidRefusedEarlierAgain() {
        // The in-process wake-up handled the deadline in the background and Android refused
        // the service (no exemption then); the deadline alarm right after has one (04 §6.2).
        val refusing = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent): ComponentName =
                throw ForegroundServiceStartNotAllowedException("app is in the background (test)")
        }
        val clock = FakeClock()
        BrewTimerServicePort(refusing, TimerClocks(clock, clock), FakeTimerDiagnostics(), TimerStateNotifications(app))
            .startForeground(testState(TimerPhase.ALERTING, clock), fullScreen = true)

        app.sendBroadcast(TimerAlarmReceiver.intent(app, TimerAlarmKind.DEADLINE))
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(app.alarms.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(TimerAlarmKind.DEADLINE)

        val command = awaitStartedService()
        assertThat(BrewTimerService.isForegroundRequired(command)).isTrue()
        val alert = checkNotNull(BrewTimerService.notificationOf(command))
        assertThat(alert.fullScreenIntent).isNotNull()
        // Shown again, it does not alert a second time.
        assertThat(alert.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    }

    private fun awaitStartedService(): Intent {
        repeat(POLLS) {
            shadowOf(app).nextStartedService?.let { return it }
            Thread.sleep(POLL_MS)
        }
        error("no service command sent")
    }

    private companion object {
        const val TIMEOUT_S = 5L
        const val POLLS = 50
        const val POLL_MS = 100L
    }
}
