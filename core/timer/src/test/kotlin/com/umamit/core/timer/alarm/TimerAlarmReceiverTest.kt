package com.umamit.core.timer.alarm

import android.Manifest
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.TestTimerHost
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.service.ForegroundServiceState
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

    private companion object {
        const val TIMEOUT_S = 5L
    }
}
